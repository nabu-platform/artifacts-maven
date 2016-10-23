package be.nabu.libs.services.maven.pom;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "project")
public class Pom extends PomArtifact {
	
	private PomArtifact parent;
	private PomDependencies dependencies;
	private PomExclusions exclusions;
	
	public PomDependencies getDependencies() {
		return dependencies;
	}

	public void setDependencies(PomDependencies dependencies) {
		this.dependencies = dependencies;
	}

	public PomArtifact getParent() {
		return parent;
	}

	public void setParent(PomArtifact parent) {
		this.parent = parent;
	}

	public PomExclusions getExclusions() {
		return exclusions;
	}
	public void setExclusions(PomExclusions exclusions) {
		this.exclusions = exclusions;
	}
	
}
