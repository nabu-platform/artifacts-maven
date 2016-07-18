package be.nabu.libs.services.maven;

import be.nabu.libs.types.java.DomainObjectFactory;

public class MavenDomainObjectFactory implements DomainObjectFactory {

	private MavenClassLoader classLoader;

	public MavenDomainObjectFactory(MavenClassLoader classLoader) {
		this.classLoader = classLoader;
	}
	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return classLoader.loadClass(name);
	}
}
