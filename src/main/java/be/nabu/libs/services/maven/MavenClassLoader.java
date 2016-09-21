package be.nabu.libs.services.maven;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.artifacts.LocalClassLoader;
import be.nabu.libs.maven.api.Artifact;
import be.nabu.libs.maven.api.Repository;
import be.nabu.libs.maven.api.WritableRepository;
import be.nabu.libs.services.maven.pom.Pom;
import be.nabu.libs.services.maven.pom.PomDependency;

/**
 * This classloader wraps around an artifact and allows you to look up classes/resources in it or its dependencies
 */
public class MavenClassLoader extends LocalClassLoader {
	
	static {
		ClassLoader.registerAsParallelCapable();
	}
	
	/**
	 * This blacklists the SPI lookups performed by jaxb to parse the POMs
	 * Otherwise we could end up in a vicious circle where the findfiles tries to parse the pom which tries to find implementations...
	 * IMPORTANT: if this list ever changes (e.g. updates to JAXB), this might be a very hard bug to spot as you would simply start generating stack overflows
	 */
	private static List<String> blacklist = Arrays.asList("META-INF/services/javax.xml.bind.JAXBContext", "META-INF/services/javax.xml.parsers.SAXParserFactory");
	
	/**
	 * Keep track of the repository we belong to so we can look up other artifacts
	 */
	private Repository mavenRepository;
	/**
	 * Keep track of our "main" artifact
	 */
	private Artifact mavenArtifact;
	/**
	 * Keeps track of the poms we have already parsed
	 */
	private Map<Artifact, Pom> poms = new HashMap<Artifact, Pom>();
	/**
	 * Keeps track of the dependency resolver allowing us to automatically fetch non-existing dependencies
	 */
	private DependencyResolver dependencyResolver;
	/**
	 * Keeps track of the entries in the zipfiles, this allows us to quickly pinpoint classes instead of having to crawl through zips for every lookup
	 */
	private Map<Artifact, List<String>> zipFiles = new HashMap<Artifact, List<String>>();
	
	private Map<String, Collection<String>> filesFound = new HashMap<String, Collection<String>>();
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	/**
	 * Externally provided dependencies (they will be resolvable by the parent classloader)
	 */
	private Set<String> provided = new HashSet<String>();
	
	public MavenClassLoader(ClassLoader parent, Repository mavenRepository, Artifact mavenArtifact, DependencyResolver dependencyResolver) {
		super(parent);
		this.mavenRepository = mavenRepository;
		this.mavenArtifact = mavenArtifact;
		this.dependencyResolver = dependencyResolver;
	}
	

	@Override
	protected Collection<String> findFiles(String fileName, boolean stopAfterFirst) {
		if (!filesFound.containsKey(fileName)) {
			List<String> files = new ArrayList<String>();
			try {
				if (hasFile(mavenArtifact, fileName)) {
					files.add(mavenArtifact.getGroupId() + "/" + mavenArtifact.getArtifactId() + "/" + mavenArtifact.getVersion() + "/" + fileName);
				}
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
			// if we have a file from the local maven artifact (which is already cached) and we only want one hit, return it
			if (!files.isEmpty() && stopAfterFirst) {
				return files;
			}
			files.addAll(findFilesInDependencies(mavenArtifact, fileName, new HashSet<String>(provided), new ArrayList<PomDependency>(), stopAfterFirst));
			synchronized(filesFound) {
				filesFound.put(fileName, files);
			}
		}
		return filesFound.get(fileName);
	}

	public void addProvided(String groupId, String artifactId) {
		provided.add(groupId + "/" + artifactId);
	}
	
	private Collection<String> findFilesInDependencies(Artifact artifact, String path, Set<String> provided, List<PomDependency> exclusions, boolean stopAfterFirst) {
		List<String> files = new ArrayList<String>();
		if (blacklist.contains(path)) {
			return files;
		}
		logger.trace("Check dependencies of artifact " + artifact.getGroupId() + "/" + artifact.getArtifactId() + " for path: " + path);
		try {
			Pom pom = poms.get(artifact);
			if (pom == null) {
				synchronized(poms) {
					pom = poms.get(artifact);
					if (pom == null) {
						pom = DependencyResolver.parsePom(artifact);
						poms.put(artifact, pom);
					}
				}
			}
			// first look through the dependencies themselves
			if (pom.getDependencies() != null) {
				if (pom.getExclusions() != null && pom.getExclusions().getExclusions() != null) {
					exclusions.addAll(pom.getExclusions().getExclusions());
				}
				for (PomDependency pomDependency : pom.getDependencies().getDependencies()) {
					if (isExcluded(pomDependency, exclusions)) {
						continue;
					}
					String key = pomDependency.getGroupId() + "/" + pomDependency.getArtifactId();
					if (!provided.contains(key) && (pomDependency.getScope() == null || pomDependency.getScope().equals("compile") || pomDependency.getScope().equals("runtime"))) {
						logger.trace("Checking dependency: " + pomDependency.getGroupId() + "/" + pomDependency.getArtifactId() + " for owner: " + pom.getGroupId() + "/" + pom.getArtifactId());
						Artifact dependency = dependencyResolver.resolve((WritableRepository) mavenRepository, pomDependency);
						if (dependency == null) {
							if (pomDependency.getOptional() != null && pomDependency.getOptional()) {
								continue;
							}
							throw new RuntimeException("Can not resolve pom dependency: " + pomDependency.getGroupId() + "/" + pomDependency.getArtifactId() + "/" + pomDependency.getVersion() + " for " + artifact.getGroupId() + "/" + artifact.getArtifactId() + "/" + artifact.getVersion());
						}
						if (hasFile(dependency, path)) {
							// in some cases the version is null in the pom dependency declaration because it is centrally managed, we don't support this atm
							String version = pomDependency.getVersion();
							// if the version contains a variable, we don't resolve it atm
							if (version == null || version.contains("${")) {
								SortedSet<String> versions = mavenRepository.getVersions(dependency.getGroupId(), dependency.getArtifactId());
								if (!versions.isEmpty()) {
									version = versions.last();
								}
							}
							if (version == null) {
								throw new RuntimeException("Can not find version for pom dependency: " + dependency.getGroupId() + "/" + dependency.getArtifactId());
							}
							files.add(key + "/" + version + "/" + path);
						}
					}
					else {
						provided.add(key);
					}
				}
				if (files.isEmpty() || !stopAfterFirst) {
					// if still not found, look deeper
					for (PomDependency pomDependency : pom.getDependencies().getDependencies()) {
						List<PomDependency> nestedExclusions = new ArrayList<PomDependency>(exclusions);
						if (pomDependency.getExclusions() != null && pomDependency.getExclusions().getExclusions() != null) { 
							nestedExclusions.addAll(pomDependency.getExclusions().getExclusions());
						}
						if (isExcluded(pomDependency, nestedExclusions)) {
							continue;
						}
						String key = pomDependency.getGroupId() + "/" + pomDependency.getArtifactId();
						if (!provided.contains(key) && (pomDependency.getScope() == null || pomDependency.getScope().equals("compile") || pomDependency.getScope().equals("runtime"))) {
							Artifact dependency = dependencyResolver.resolve((WritableRepository) mavenRepository, pomDependency);
//							Artifact dependency = mavenRepository.getArtifact(pomDependency.getGroupId(), pomDependency.getArtifactId(), pomDependency.getVersion(), false);
							if (dependency == null) {
								if (pomDependency.getOptional() != null && pomDependency.getOptional()) {
									continue;
								}
								throw new RuntimeException("Can not resolve pom dependency: " + pomDependency.getGroupId() + "/" + pomDependency.getArtifactId() + "/" + pomDependency.getVersion());					
							}
							files.addAll(findFilesInDependencies(dependency, path, provided, nestedExclusions, stopAfterFirst));
						}
					}
				}
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
		return files;
	}
	
	protected boolean hasFile(Artifact artifact, String path) throws IOException {
		boolean hasFile = false;
		if (!zipFiles.containsKey(artifact)) {
			synchronized(zipFiles) {
				if (!zipFiles.containsKey(artifact)) {
					logger.trace("Scanning zipped files from artifact " + artifact.getGroupId() + "/" + artifact.getArtifactId() + " for path: " + path);
					List<String> files = new ArrayList<String>();
					ZipInputStream zip = new ZipInputStream(artifact.getContent());
					try {
						ZipEntry entry = null;
						while ((entry = zip.getNextEntry()) != null) {
							String entryName = entry.getName();
							if (entryName.startsWith("/")) {
								entryName = entryName.substring(1);
							}
							if (entryName.equals(path)) {
								hasFile = true;
							}
							files.add(entryName);
						}
					}
					finally {
						zip.close();
					}
					zipFiles.put(artifact, files);
				}
			}
		}
		else {
			hasFile = zipFiles.get(artifact).contains(path);
		}
		return hasFile;
	}
	
	@Override
	protected byte[] readFile(String fileName) {
		// the filename is built using the "key" we generated in the find which has the groupId/artifactId/version in it
		String [] parts = fileName.split("/");
		try {
			Artifact artifact = mavenRepository.getArtifact(parts[0], parts[1], parts[2], false);
			if (artifact == null) {
				throw new IllegalArgumentException("The filename '" + fileName + "' does not belong to an artifact in this repository");
			}
			// start after the last trailing slash of the version
			return findFile(artifact, fileName.substring(parts[0].length() + parts[1].length() + parts[2].length() + 3));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private boolean isExcluded(PomDependency dependency, List<PomDependency> exclusions) {
		for (PomDependency exclusion : exclusions) {
			if ("*".equals(exclusion.getGroupId())) {
				return true;
			}
			else if (dependency.getGroupId().equals(exclusion.getGroupId()) && "*".equals(exclusion.getArtifactId())) {
				return true;
			}
			else if (dependency.getGroupId().equals(exclusion.getGroupId()) && dependency.getArtifactId().equals(exclusion.getArtifactId())) {
				return true;
			}
		}
		return false;
	}
	
	@SuppressWarnings("unused")
	private byte [] findClassInRepository(String path) throws IOException {
		for (String groupId : getRepository().getGroups()) {
			for (String artifactId : getRepository().getArtifacts(groupId)) {
				List<String> versions = new ArrayList<String>(getRepository().getVersions(groupId, artifactId));
				Collections.reverse(versions);
				for (String version : versions) {
					Artifact artifact = getRepository().getArtifact(groupId, artifactId, version, false);
					if (artifact != null && (artifact.getPackaging().equals("jar") || artifact.getPackaging().equals("bundle"))) {
						byte [] bytes = findFile(artifact, path);
						if (bytes != null) {
							logger.trace("Found " + path + " outside of dependencies in " + artifact.getGroupId() + "/" + artifact.getArtifactId());
							return bytes;
						}
					}
				}
			}
		}
		return null;
	}
	
	protected byte [] findFile(Artifact artifact, String path) throws IOException {
		if (!zipFiles.containsKey(artifact)) {
			throw new IOException("The artifact '" + artifact + "' has not been scanned yet");
		}
		logger.trace("Loading content from artifact " + artifact.getGroupId() + "/" + artifact.getArtifactId() + " for path: " + path);
		// actually get it
		ZipInputStream zip = new ZipInputStream(new BufferedInputStream(artifact.getContent()));
		try {
			ZipEntry entry = null;
			while ((entry = zip.getNextEntry()) != null) {
				String entryName = entry.getName();
				if (entryName.startsWith("/")) {
					entryName = entryName.substring(1);
				}
				if (entryName.equals(path)) {
					return toBytes(zip);
				}
			}
		}
		finally {
			zip.close();
		}
		return null;
	}

	public static byte[] toBytes(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		int read = 0;
		byte [] buffer = new byte[4096];
		while ((read = input.read(buffer)) != -1) {
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	public Repository getRepository() {
		return mavenRepository;
	}
	
	@Override
	public String toString() {
		return "MavenClassloader for artifact " + mavenArtifact.getGroupId() + "/" + mavenArtifact.getArtifactId();
	}

}
