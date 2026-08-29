package com.coolxer.service;

import java.io.InputStream;

/**
 * 日志监控服务接口
 * <p>
 * 提供异步监控进程日志输出的功能
 * </p>
 */
public interface MonitorService {
    /**
     * 监控日志
     * <p>
     * 异步读取输入流并写入日志文件，支持日志文件大小限制
     * </p>
     * @param format 日志文件路径格式
     * @param rootPath 根路径
     * @param path 子路径
     * @param inputStream 输入流
     */
    void monitorLog(String format, String rootPath, String path, InputStream inputStream);
}