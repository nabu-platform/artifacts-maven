/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

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
	// provided artifacts (groupId:artifactId syntax)
	private List<String> artifactsToIgnore;

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
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setValidating(false);
			factory.setNamespaceAware(false);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			// allow no external access, as defined http://docs.oracle.com/javase/7/docs/api/javax/xml/XMLConstants.html#FEATURE_SECURE_PROCESSING an empty string means no protocols are allowed
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			Document parse = factory.newDocumentBuilder().parse(input);
			
			JAXBContext context = JAXBContext.newInstance(Pom.class);
			return (Pom) context.createUnmarshaller().unmarshal(parse);
//			return TypeUtils.getAsBean(binding.unmarshal(input, new Window[0]), Pom.class);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		finally {
			input.close();
		}
	}
	
	public void resolve(WritableRepository repository, Artifact artifact) throws IOException, ParseException {
		Pom pom = parsePom(artifact);
		if (pom.getDependencies() != null) {
			for (PomDependency dependency : pom.getDependencies().getDependencies()) {
				Artifact resolved = resolve(repository, dependency);
				if (resolved == null) {
					logger.warn("[" + artifact.getGroupId() + "::" + artifact.getArtifactId() + "] The pom dependency " + dependency.getGroupId() + "/" + dependency.getArtifactId() + " does not exist");
				}
			}
		}
	}
	
	public Artifact resolve(WritableRepository repository, PomDependency dependency) throws IOException {
		String groupId = dependency.getGroupId().trim();
		if (groupId.contains("${")) {
			boolean found = false;
			for (String group : repository.getGroups()) {
				if (group == null) {
					continue;
				}
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
		if (artifactsToIgnore != null && artifactsToIgnore.contains(dependency.getGroupId() + ":" + dependency.getArtifactId())) {
			return current;
		}
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
		if (dependency.getVersion() != null && (current == null || (!updatedSnapshots.contains(dependency) && current.getVersion().endsWith("-SNAPSHOT") && updateSnapshots))) {
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

	public List<String> getArtifactsToIgnore() {
		return artifactsToIgnore;
	}

	public void setArtifactsToIgnore(List<String> artifactsToIgnore) {
		this.artifactsToIgnore = artifactsToIgnore;
	}
	
	public boolean isIgnored(String groupId, String artifactId) {
		return artifactsToIgnore != null && (artifactsToIgnore.contains(groupId) || artifactsToIgnore.contains(groupId + ":" + artifactId));
	}
}
