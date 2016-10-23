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
