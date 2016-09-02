package be.nabu.libs.services.maven;

import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import javax.xml.bind.JAXBContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.maven.api.Artifact;
import be.nabu.libs.maven.api.WritableRepository;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.services.maven.pom.Pom;
import be.nabu.libs.services.maven.pom.PomDependency;

public class DependencyResolver {
	
	private URI [] endpoints;
	private Proxy proxy;
	private List<PomDependency> updatedSnapshots = new ArrayList<PomDependency>();
	private boolean updateSnapshots = true;

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	public DependencyResolver(URI...endpoints) {
		this.endpoints = endpoints;
	}
	
	public Proxy getProxy() {
		return proxy;
	}

	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}

	public static Pom parsePom(Artifact artifact) throws IOException, ParseException {
		// we don't use XMLBinding (anymore) because it drags in way too many dependencies
		// the pom parsing is done by the maven classloader who ideally should not depend on too many classes
		// if there wasn't the default JAXB solution, even a tiny optimized parsing class would be better than using the XMLBinding in this case
//		XMLBinding binding = new XMLBinding(new BeanType<Pom>(Pom.class), Charset.forName("UTF-8"));
		InputStream input = artifact.getPom();
		try {
			JAXBContext context = JAXBContext.newInstance(Pom.class);
			return (Pom) context.createUnmarshaller().unmarshal(input);
//			return TypeUtils.getAsBean(binding.unmarshal(input, new Window[0]), Pom.class);
		}
		catch (Exception e) {
			throw new IOException(e);
		}
		finally {
			input.close();
		}
	}
	
	public void resolve(WritableRepository repository, Artifact artifact) throws IOException, ParseException {
		Pom pom = parsePom(artifact);
		if (pom.getDependencies() != null) {
			for (PomDependency dependency : pom.getDependencies().getDependencies()) {
				resolve(repository, dependency);
			}
		}
	}
	
	public Artifact resolve(WritableRepository repository, PomDependency dependency) throws IOException {
		String groupId = dependency.getGroupId().trim();
		if (groupId.contains("${")) {
			boolean found = false;
			for (String group : repository.getGroups()) {
				if (repository.getArtifacts(group).contains(dependency.getArtifactId())) {
					found = true;
					groupId = group;
					dependency.setGroupId(groupId);
					break;
				}
			}
			if (!found && dependency.getOptional() != null && dependency.getOptional()) {
				return null;
			}
		}
		Artifact current = null;
		// sometimes the version is not filled in cause it is centrally managed or it might contain a variable
		if (dependency.getVersion() == null || dependency.getVersion().contains("${")) {
			SortedSet<String> versions = repository.getVersions(groupId, dependency.getArtifactId());
			if (!versions.isEmpty()) {
				dependency.setVersion(versions.last());
				current = repository.getArtifact(groupId, dependency.getArtifactId().trim(), versions.last().trim(), false);
			}
		}
		else {
			current = repository.getArtifact(groupId, dependency.getArtifactId().trim(), dependency.getVersion().trim(), false);
		}
		if (current == null && dependency.getOptional() != null && dependency.getOptional()) {
			return current;
		}
		// if it doesn't exist or is a snapshot, refresh
		if (current == null || (!updatedSnapshots.contains(dependency) && current.getVersion().endsWith("-SNAPSHOT") && updateSnapshots)) {
			if (current == null) {
				logger.info("The pom dependency " + dependency.getGroupId() + "/" + dependency.getArtifactId() + " does not exist");
			}
			else {
				logger.info("Checking for updates to the snapshot " + dependency.getGroupId() + "/" + dependency.getArtifactId());
			}
			// whether or not it succeeds, we don't want to try again
			updatedSnapshots.add(dependency);
			for (URI endpoint : endpoints) {
				// path to the artifact
				String path = groupId.replace('.', '/') + "/" + dependency.getArtifactId().trim() + "/" + dependency.getVersion().trim();
				// path with the artifact
				path += "/" + dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar";
				URI child = URIUtils.getChild(endpoint, path);
				logger.info("Try to retrieve artifact from: " + child);
				try {
					URLConnection connection = getProxy() == null ? child.toURL().openConnection() : child.toURL().openConnection(getProxy());
					InputStream input = connection.getInputStream();
					try {
						current = repository.create(groupId, dependency.getArtifactId().trim(), dependency.getVersion().trim(), "jar", input, false);
						break;
					}
					finally {
						input.close();
					}
				}
				catch (Exception e) {
					// ignore, we will try another endpoint
				}
			}
		}
		return current;
	}

	public boolean isUpdateSnapshots() {
		return updateSnapshots;
	}

	public void setUpdateSnapshots(boolean updateSnapshots) {
		this.updateSnapshots = updateSnapshots;
	}
}
