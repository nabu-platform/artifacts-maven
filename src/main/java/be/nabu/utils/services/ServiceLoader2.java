package be.nabu.utils.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.maven.api.Artifact;
import be.nabu.libs.services.maven.MavenClassLoader;

/**
 * Source code of the class in resources (renamed though)
 */
public class ServiceLoader2<S> implements Iterable<S> {
	
	private static Map<Class<?>, ServiceLoader2<?>> serviceLoaders = new HashMap<Class<?>, ServiceLoader2<?>>();
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	private Class<S> clazz;
	private List<S> instances;

	private ServiceLoader2(Class<S> clazz) {
		this.clazz = clazz;
		logger.info("Starting custom serviceloader for " + clazz);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <S> List<S> load(Class<S> clazz) {
		if (!serviceLoaders.containsKey(clazz)) {
			synchronized (serviceLoaders) {
				if (!serviceLoaders.containsKey(clazz)) {
					serviceLoaders.put(clazz, new ServiceLoader2(clazz));
				}
			}
		}
		return (List<S>) serviceLoaders.get(clazz).getInstances();
	}

	@SuppressWarnings("unchecked")
	private List<S> getInstances() {
		if (instances == null) {
			synchronized(this) {
				if (instances == null) {
					logger.info("Finding SPI references for: " + clazz.getName() + " in classloader: " + getClass().getClassLoader());
					instances = new ArrayList<S>();
					MavenClassLoader loader = (MavenClassLoader) getClass().getClassLoader();
					try {
						for (String groupId : loader.getRepository().getGroups()) {
							for (String artifactId : loader.getRepository().getArtifacts(groupId)) {
								List<String> versions = new ArrayList<String>(loader.getRepository().getVersions(groupId, artifactId));
								Collections.reverse(versions);
								for (String version : versions) {
									logger.debug("Checking " + groupId + "/" + artifactId + "/" + version);
									Artifact artifact = loader.getRepository().getArtifact(groupId, artifactId, version, false);
									if (artifact != null && (artifact.getPackaging().equals("jar") || artifact.getPackaging().equals("bundle"))) {
										ZipInputStream input = new ZipInputStream(artifact.getContent());
										try {
											ZipEntry entry = null;
											while ((entry = input.getNextEntry()) != null) {
												String name = entry.getName().replaceAll("^[/]+", "");
												if (name.equals("META-INF/services/" + clazz.getName())) {
													byte [] content = MavenClassLoader.toBytes(input);
													for (String implementation : new String(content).trim().replaceAll("\r", "").split("\n")) {
														logger.debug("Found implementation: " + implementation);
														try {
															instances.add((S) loader.loadClass(implementation).newInstance());
														}
														catch (ClassNotFoundException e) {
															// ignore
														}
														catch (InstantiationException e) {
															// ignore
														}
														catch (IllegalAccessException e) {
															// ignore
														}
													}
												}
											}
										}
										finally {
											input.close();
										}
									}
								}
							}
						}
					}
					catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
		return instances;
	}
	
	@Override
	public Iterator<S> iterator() {
		return getInstances().iterator();
	}
	
}
