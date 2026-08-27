package com.coolxer.configuration.extend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExtendJarManager {

    private final static String SCAN_PACKAGE = "com.coolxer.plugin";

    private final Map<String, ExtendJarEntry> extendJarEntryConcurrentHashMap = new ConcurrentHashMap<>();

    @Autowired
    private Registrar registrar;
    @Autowired
    private Cleaner cleaner;

    public synchronized boolean load(String pluginId, File jar) throws Exception {
        if (extendJarEntryConcurrentHashMap.containsKey(pluginId)) {
            //已经加载
            return false;
        } else {
            ExtendJarClassLoader classLoader = new ExtendJarClassLoader(jar.toURI().toURL(), this.getClass().getClassLoader());
            ExtendJar extendJar = new ExtendJar(pluginId, jar, SCAN_PACKAGE);
            try {
                registrar.register(extendJar, classLoader);
                extendJarEntryConcurrentHashMap.put(pluginId, new ExtendJarEntry(classLoader, extendJar));
                return true;
            } catch (Exception e) {
                cleaner.cleanup(extendJar);
                classLoader.close();
                throw e;
            }
        }
    }

    public synchronized void unload(String pluginId) throws Exception {
        ExtendJarEntry entry = extendJarEntryConcurrentHashMap.remove(pluginId);
        if (entry != null) {
            cleaner.cleanup(new ExtendJar(pluginId, null, null));
            entry.classLoader().close();
        }
    }

    public record ExtendJarEntry(URLClassLoader classLoader, ExtendJar descriptor) {
    }

    public class ExtendJarClassLoader extends URLClassLoader {
        public ExtendJarClassLoader(URL jarUrl, ClassLoader parent) {
            super(new URL[]{jarUrl}, parent);
        }
    }
}
