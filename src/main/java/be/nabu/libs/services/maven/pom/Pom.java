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
