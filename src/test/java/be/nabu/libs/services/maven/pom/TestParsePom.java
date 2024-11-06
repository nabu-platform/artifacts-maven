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

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Date;

import be.nabu.libs.maven.api.Artifact;
import be.nabu.libs.services.maven.DependencyResolver;
import junit.framework.TestCase;

public class TestParsePom extends TestCase {
	
	public void testParse() throws IOException, ParseException {
		Pom pom = parse("pom.xml");
		assertEquals("artifacts-maven", pom.getArtifactId());
		assertEquals("be.nabu.libs.artifacts", pom.getGroupId());
		assertEquals("1.0-SNAPSHOT", pom.getVersion());
	}
	
	public void testParse2() throws IOException, ParseException {
		Pom pom = parse("pom2.xml");
		assertEquals("eai-module-types-xml", pom.getArtifactId());
		assertEquals("nabu", pom.getGroupId());
		assertEquals("1.0", pom.getVersion());
		assertEquals(4, pom.getDependencies().getDependencies().size());
	}

	private Pom parse(final String name) throws IOException, ParseException {
		return DependencyResolver.parsePom(new Artifact() {
			@Override
			public String getArtifactId() {
				return null;
			}
			@Override
			public InputStream getContent() throws IOException {
				return null;
			}
			@Override
			public String getGroupId() {
				return null;
			}
			@Override
			public Date getLastModified() {
				return null;
			}
			@Override
			public String getPackaging() {
				return null;
			}
			@Override
			public InputStream getPom() throws IOException {
				return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
			}
			@Override
			public String getVersion() {
				return null;
			}
			@Override
			public boolean isTest() {
				return false;
			}
		});
	}
}
