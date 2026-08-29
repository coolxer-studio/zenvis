package com.coolxer.service.impl;

import com.coolxer.service.MonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 日志监控服务实现类
 * <p>
 * 异步监控进程日志输出并写入文件，支持日志文件大小限制
 * </p>
 */
@Slf4j
@Service
public class MonitorServiceImpl implements MonitorService {

    private static final int MAX_LOG_SIZE = 1024 * 1024;

    /**
     * 监控日志
     * <p>
     * 异步读取输入流并写入日志文件，当文件大小超过限制时重置内容
     * </p>
     * @param format 日志文件路径格式
     * @param rootPath 根路径
     * @param path 子路径
     * @param inputStream 输入流
     */
    @Async("logMonitorExecutor")
    @Override
    public void monitorLog(String format, String rootPath, String path, InputStream inputStream) {
        Path logPath = Path.of(String.format(format, rootPath, path));
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            File logFile = logPath.toFile();
            if (!logFile.exists()) {
                Files.writeString(logPath, "create.......\n", StandardOpenOption.CREATE);
            }

            String line;
            int lineLength = 0;
            StringBuilder buffer = new StringBuilder();
            
            while ((line = reader.readLine()) != null) {
                if (!VectorServiceImpl.hasProcess(path)) {
                    log.debug("Process {} no longer exists, stopping log monitoring", path);
                    break;
                }
                
                lineLength += line.length() + 1;
                buffer.append(line).append("\n");
                
                if (lineLength > MAX_LOG_SIZE) {
                    log.info("Log file {} exceeds max size, resetting content", logFile.getAbsolutePath());
                    Files.writeString(logPath, "empty.......\n");
                    lineLength = 0;
                    buffer.setLength(0);
                }
                
                if (buffer.length() >= 4096) {
                    flushBuffer(logPath, buffer);
                }
            }
            
            if (buffer.length() > 0) {
                flushBuffer(logPath, buffer);
            }
            
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Stream closed")) {
                log.debug("Log monitoring stream closed for path: {}", path);
            } else {
                log.error("Failed to monitor log for path: {}", path, e);
            }
        }
    }

    /**
     * 刷新缓冲区到文件
     * @param logPath 日志文件路径
     * @param buffer 缓冲区内容
     * @throws IOException IO异常
     */
    private void flushBuffer(Path logPath, StringBuilder buffer) throws IOException {
        Files.writeString(logPath, buffer.toString(), StandardOpenOption.APPEND);
        buffer.setLength(0);
    }
}
