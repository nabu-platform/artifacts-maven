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
