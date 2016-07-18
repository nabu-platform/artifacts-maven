package be.nabu.libs.services.maven;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import be.nabu.libs.artifacts.LocalClassLoader;
import be.nabu.libs.artifacts.api.ClassProvidingArtifact;
import be.nabu.libs.maven.api.Artifact;
import be.nabu.libs.maven.api.DomainRepository;
import be.nabu.libs.services.pojo.MethodService;
import be.nabu.libs.types.api.DefinedTypeResolver;
import be.nabu.libs.types.java.BeanType;

public class MavenArtifact implements ClassProvidingArtifact {

	private Artifact artifact;
	private String id;
	private DomainRepository repository;
	private MavenClassLoader classLoader;
	private DependencyResolver resolver;
	private Map<String, be.nabu.libs.artifacts.api.Artifact> children;
	private DefinedTypeResolver definedTypeResolver;
	private Map<Class<?>, List<Class<?>>> implementations;
	
	public MavenArtifact(ClassLoader classLoader, DefinedTypeResolver definedTypeResolver, DependencyResolver resolver, DomainRepository repository, String id, Artifact artifact) {
		this.definedTypeResolver = definedTypeResolver;
		this.resolver = resolver;
		this.repository = repository;
		this.id = id;
		this.artifact = artifact;
		this.classLoader = new MavenClassLoader(classLoader, repository, artifact, resolver);
	}

	@Override
	public String getId() {
		return id;
	}

	public Artifact getArtifact() {
		return artifact;
	}

	public DomainRepository getRepository() {
		return repository;
	}

	public MavenClassLoader getClassLoader() {
		return classLoader;
	}

	public DependencyResolver getResolver() {
		return resolver;
	}
	
	public Annotation[] getAnnotations(String childId) throws IOException {
		if (getChildren().get(childId) instanceof MethodService) {
			return ((MethodService) getChildren().get(childId)).getMethod().getAnnotations();
		}
		else if (getChildren().get(childId) instanceof BeanType) {
			return ((BeanType<?>) getChildren().get(childId)).getBeanClass().getAnnotations();
		}
		return new Annotation[0];
	}
	
	public Map<String, be.nabu.libs.artifacts.api.Artifact> getChildren() throws IOException {
		if (children == null) {
			MavenScanner scanner = new MavenScanner();
			children = scanner.scan(definedTypeResolver, getClassLoader(), getResolver(), getRepository(), getArtifact());
		}
		return children;
	}
	
	public Map<Class<?>, List<Class<?>>> getImplementations() throws IOException {
		if (implementations == null) {
			MavenScanner scanner = new MavenScanner();
			implementations = scanner.scanSPI(getClassLoader(), getArtifact());
		}
		return implementations;
	}

	@Override
	public List<Class<?>> getImplementationsFor(Class<?> clazz) throws IOException {
		return getImplementations().get(clazz);
	}

	@Override
	public Class<?> loadClass(String id) throws ClassNotFoundException {
		return getClassLoader().loadClassNonRecursively(id);
	}

	@Override
	public InputStream loadResource(String id) {
		return getClassLoader().getResourceAsStream(id);
	}

	@Override
	public Collection<LocalClassLoader> getClassLoaders() {
		return Arrays.asList(new LocalClassLoader [] { getClassLoader() });
	}
	
}
