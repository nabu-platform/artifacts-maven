package be.nabu.libs.services.maven.pom;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;

public class PomExclusions {
	private List<PomDependency> exclusions = new ArrayList<PomDependency>();

	@XmlElement(name = "exclusion")
	public List<PomDependency> getExclusions() {
		return exclusions;
	}
	public void setExclusions(List<PomDependency> exclusions) {
		this.exclusions = exclusions;
	}
}
