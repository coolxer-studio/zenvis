package com.coolxer.configuration.extend;

import lombok.Getter;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

@Getter
public class ExtendJar {
    private static final AtomicLong LOAD_SEQUENCE = new AtomicLong();

    private String id;   // 唯一标识,建议用插件包名
    private File jarFile;
    private String scanPackage; // 可选，缩小扫描范围
    private String beanNamePrefix;
    private String beanNameNamespace;
    private String urlPrefix;

    public ExtendJar(String id, File jarFile, String scanPackage) {
        this.id = id;
        this.jarFile = jarFile;
        this.scanPackage = scanPackage;
        this.beanNamePrefix = this.id + ".";
        this.beanNameNamespace = this.beanNamePrefix + LOAD_SEQUENCE.incrementAndGet() + ".";
        this.urlPrefix = "/api/v1/plugin/" + id;
    }

    public String beanNameBuild(String beanName) {
        return beanNameNamespace + beanName;
    }

    public String fullPathBuild(String path) {
        if (path == null || path.isEmpty()) {
            return urlPrefix;
        }
        if (path.startsWith("/")) {
            return urlPrefix + path;
        } else {
            return urlPrefix + "/" + path;
        }
    }
}
