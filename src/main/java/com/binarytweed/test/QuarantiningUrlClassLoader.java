package com.binarytweed.test;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuarantiningUrlClassLoader extends ClassLoader {
	private static final Logger logger = LoggerFactory.getLogger(QuarantiningUrlClassLoader.class);
	private final Set<String> quarantinedClassNames;

	public QuarantiningUrlClassLoader(String... quarantinedClassNames) {
		super(getSystemClassLoader());
		this.quarantinedClassNames = new HashSet<>(asList(quarantinedClassNames));
	}

	private boolean shouldLoad(String className) {
		return !className.equals(getClass().getName()) && !className.endsWith("QuarantiningUrlClassLoader") && quarantine(className);
	}

	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		if (!shouldLoad(name)) {
			logger.trace("loading class {} by parent");
			return super.loadClass(name);
		}
		logger.trace("loading class {} in quarantine");
		return doLoadClass(name);
	}

	private Class<?> doLoadClass(String name) throws ClassNotFoundException {
		String internalName = StringUtils.replace(name, ".", "/") + ".class";
		InputStream is = super.getResourceAsStream(internalName);
		if (is == null) {
			throw new ClassNotFoundException(name);
		}
		try {
			byte[] bytes = IOUtils.toByteArray(is);
			Class<?> cls = defineClass(name, bytes, 0, bytes.length);
			// Additional check for defining the package, if not defined yet.
			if (cls.getPackage() == null) {
				int packageSeparator = name.lastIndexOf('.');
				if (packageSeparator != -1) {
					String packageName = name.substring(0, packageSeparator);
					definePackage(packageName, null, null, null, null, null, null, null);
				}
			}
			return cls;
		}
		catch (IOException ex) {
			throw new ClassNotFoundException("Cannot load resource for class [" + name + "]", ex);
		}
	}

	private boolean quarantine(String className) {
		return quarantinedClassNames.stream().anyMatch(className::startsWith);
	}
}
