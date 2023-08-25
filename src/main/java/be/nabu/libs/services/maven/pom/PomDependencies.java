package be.nabu.libs.services.maven.pom;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

public class PomDependencies {
	private List<PomDependency> dependencies = new ArrayList<PomDependency>();

	@XmlElement(name = "dependency")
	public List<PomDependency> getDependencies() {
		return dependencies;
	}

	public void setDependencies(List<PomDependency> dependencies) {
		this.dependencies = dependencies;
	}

	@Override
	public String toString() {
		return "PomDependencies" + dependencies;
	}
}
