package be.nabu.libs.services.maven;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.jws.WebService;
import javax.xml.bind.annotation.XmlRootElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.maven.api.Artifact;
import be.nabu.libs.maven.api.DomainRepository;
import be.nabu.libs.services.pojo.MethodService;
import be.nabu.libs.services.pojo.MethodServiceInterface;
import be.nabu.libs.types.DefinedSimpleTypeResolver;
import be.nabu.libs.types.MultipleDefinedTypeResolver;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeResolver;
import be.nabu.libs.types.api.annotation.ComplexTypeDescriptor;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.utils.io.IOUtils;

public class MavenScanner {
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	public Map<String, be.nabu.libs.artifacts.api.Artifact> scan(DefinedTypeResolver definedTypeResolver, DependencyResolver dependencyResolver, DomainRepository repository) throws IOException {
		Map<String, be.nabu.libs.artifacts.api.Artifact> artifacts = new HashMap<String, be.nabu.libs.artifacts.api.Artifact>();
		for (Artifact artifact : repository.getInternalArtifacts()) {
			MavenClassLoader loader = new MavenClassLoader(Thread.currentThread().getContextClassLoader(), repository, artifact, dependencyResolver);
			if (artifact.getPackaging().equals("jar")) {
				artifacts.putAll(scan(definedTypeResolver, loader, dependencyResolver, repository, artifact));
			}
		}
		return artifacts;
	}
	
	public Map<Class<?>, List<Class<?>>> scanSPI(MavenClassLoader loader, Artifact artifact) throws IOException {
		Map<Class<?>, List<Class<?>>> spi = new HashMap<Class<?>, List<Class<?>>>();
		ZipInputStream zip = new ZipInputStream(artifact.getContent());
		try {
			ZipEntry entry = null;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				if (name.startsWith("/")) {
					name = name.substring(1);
				}
				if (name.startsWith("META-INF/services/")) {
					String ifaceName = name.substring("META-INF/services/".length());
					if (!ifaceName.trim().isEmpty()) {
						try {
							Class<?> iface = loader.loadClass(ifaceName);
							List<Class<?>> implementations = new ArrayList<Class<?>>();
							String content = new String(IOUtils.toBytes(IOUtils.wrap(zip)), "UTF-8");
							for (String line : content.split("[\r\n]+")) {
								try {
									Class<?> implementation = loader.loadClass(line);
									implementations.add(implementation);
								}
								catch (ClassNotFoundException e) {
									logger.error("Could not locate implementation class '" + line + "' for interface: " + ifaceName);
								}
							}
							spi.put(iface, implementations);
						}
						catch (ClassNotFoundException e) {
							logger.error("Could not locate interface: " + ifaceName);
						}
					}
				}
			}
		}
		finally {
			zip.close();
		}
		return spi;
	}
	
	public Map<String, be.nabu.libs.artifacts.api.Artifact> scan(DefinedTypeResolver definedTypeResolver, MavenClassLoader loader, DependencyResolver dependencyResolver, DomainRepository repository, Artifact artifact) throws IOException {
		// apart from the "general" type resolver used for the entire server
		// we also need a type resolver that works specifically at the level of this maven artifact
		// note that we _first_ use the general resolver, only then try the specific one
		// this means if a class in your maven artifact also exists in the main, it will take the main one!
		// this is however necessary because the main resolver resolves way more than only beans
		// and specifically for simple types, if the bean resolver if set first, it will resolve _any_ java object
		BeanResolver domainResolver = new BeanResolver();
		domainResolver.addFactory(new MavenDomainObjectFactory(loader));
		DefinedTypeResolver combinedResolver = new MultipleDefinedTypeResolver(Arrays.asList(
			new DefinedSimpleTypeResolver(SimpleTypeWrapperFactory.getInstance().getWrapper(), loader),
			domainResolver,
			definedTypeResolver
		));
		logger.info("Scanning artifact " + artifact.getGroupId() + "/" + artifact.getArtifactId() + " for java classes to expose");
		Map<String, be.nabu.libs.artifacts.api.Artifact> children = new HashMap<String, be.nabu.libs.artifacts.api.Artifact>();
		ZipInputStream zip = new ZipInputStream(artifact.getContent());
		try {
			ZipEntry entry = null;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				if (name.endsWith(".class")) {
					if (name.startsWith("/")) {
						name = name.substring(1);
					}
					// strip the extension
					name = name.substring(0, name.length() - ".class".length());
					name = name.replace('/', '.');
					logger.debug("Scanning class: " + name);
					// only scan things that are within the defined groupId
					if (name.startsWith(artifact.getGroupId())) {
						try {
							Class<?> clazz = loader.loadClass(name);
							// if you have the root element annotation, add it
							if (clazz.getAnnotation(XmlRootElement.class) != null || clazz.getAnnotation(ComplexTypeDescriptor.class) != null) {
								DefinedType type = BeanResolver.getInstance().resolve(clazz);
								children.put(getRelativeId(artifact.getGroupId(), type), type);
							}
							else if (clazz.getAnnotation(WebService.class) != null) {
								for (Method method : clazz.getDeclaredMethods()) {
									if (Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())) {
										if (clazz.isInterface()) {
											MethodServiceInterface methodServiceInterface = MethodServiceInterface.wrap(combinedResolver, method);
											children.put(getRelativeId(artifact.getGroupId(), methodServiceInterface), methodServiceInterface);
										}
										else {
											MethodService methodService = new MethodService(combinedResolver, clazz, method);
											children.put(getRelativeId(artifact.getGroupId(), methodService), methodService);
										}
									}
								}
							}
						}
						catch (ClassNotFoundException e) {
							// this is an implementation error
							throw new RuntimeException(e);
						}
					}
				}
			}
		}
		finally {
			zip.close();
		}
		return children;
	}
	
	private String getRelativeId(String groupId, be.nabu.libs.artifacts.api.Artifact artifact) {
		String relativeId = artifact.getId().replaceFirst(Pattern.quote(groupId), "");
		if (relativeId.startsWith(".")) {
			relativeId = relativeId.substring(1);
		}
		return relativeId;
	}
}
