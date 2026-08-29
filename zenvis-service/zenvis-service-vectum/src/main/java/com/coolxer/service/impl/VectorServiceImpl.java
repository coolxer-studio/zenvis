package com.coolxer.service.impl;

import com.coolxer.model.LuaFile;
import com.coolxer.service.MonitorService;
import com.coolxer.service.VectorService;
import com.coolxer.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量服务实现类
 * <p>
 * 管理向量进程的生命周期，包括创建、启动、停止、删除等操作
 * </p>
 */
@Slf4j
@Service
public class VectorServiceImpl implements VectorService {

    private static final ConcurrentHashMap<String, Process> PROCESS_CACHE = new ConcurrentHashMap<>();

    private static final String CMD_FORMAT = "%s/bin/vector";
    private static final String CONFIG_FORMAT = "%s/%s/push.%s";
    private static final String PID_FORMAT = "%s/%s/pid";
    private static final String LUA_PATH_FORMAT = "%s/%s/lua";
    private static final String LOG_INFO_FORMAT = "%s/%s/info.log";
    private static final String LOG_ERROR_FORMAT = "%s/%s/error.log";

    private final MonitorService monitorService;
    private final String vectorHomePath;
    private final String taskWorkspace;
    private String cmdPath;

    public VectorServiceImpl(MonitorService monitorService, 
                            @Value("${vector.home}") String vectorHomePath,
                            @Value("${task.workspace:${vector.home}/workspace}") String taskWorkspace) {
        this.monitorService = monitorService;
        this.vectorHomePath = vectorHomePath;
        this.taskWorkspace = taskWorkspace;
    }

    /**
     * 初始化服务
     * <p>
     * 应用启动时调用，恢复之前运行的任务进程
     * </p>
     */
    @PostConstruct
    @Override
    public void init() {
        cmdPath = String.format(CMD_FORMAT, vectorHomePath);
        
        File rootPath = new File(taskWorkspace);
        if (rootPath.exists()) {
            File[] taskDirList = rootPath.listFiles();
            if (taskDirList != null) {
                for (File taskDir : taskDirList) {
                    String taskDirName = taskDir.getName();
                    File pidFile = new File(String.format(PID_FORMAT, taskWorkspace, taskDirName));
                    
                    if (pidFile.exists()) {
                        String pid = readFile(pidFile.getAbsolutePath());
                        if (pid != null && !pid.isEmpty()) {
                            if (cmdKill(pid)) {
                                log.info("Stopped existing process: {}", pid);
                            } else {
                                log.warn("Failed to stop existing process: {}", pid);
                            }
                        }
                        startProcess(taskDirName);
                    }
                }
            }
        } else {
            boolean created = rootPath.mkdirs();
            if (created) {
                log.info("Created task root directory: {}", taskWorkspace);
            } else {
                log.error("Failed to create task root directory: {}", taskWorkspace);
            }
        }
    }

    /**
     * 创建进程配置
     * @param path 进程路径
     * @param configContext 配置内容
     * @param luaFiles Lua脚本文件列表
     * @return 错误信息，成功返回null
     */
    @Override
    public String createProcess(String path, String configContext, ArrayList<LuaFile> luaFiles) {
        try {
            File taskPath = new File(taskWorkspace + File.separator + path);
            if (!taskPath.exists()) {
                taskPath.mkdirs();
            }
            
            File luaPath = new File(String.format(LUA_PATH_FORMAT, taskWorkspace, path));
            if (!luaPath.exists()) {
                luaPath.mkdirs();
            }
            
            if (luaFiles != null) {
                for (LuaFile luaFile : luaFiles) {
                    Path luaFilePath = Path.of(luaPath.getAbsolutePath() + File.separator + luaFile.getFileName());
                    Files.writeString(luaFilePath, luaFile.getContext(), StandardOpenOption.CREATE);
                }
            }
            
            String extension = detectFormat(configContext);
            Path configFilePath = Path.of(String.format(CONFIG_FORMAT, taskWorkspace, path, extension));
            Files.writeString(configFilePath, configContext, StandardOpenOption.CREATE);
            
        } catch (IOException e) {
            log.error("Failed to create process directory structure for path: {}", path, e);
            return e.getMessage();
        }
        return null;
    }

    private String detectFormat(String content) {
        if (content == null || content.isEmpty()) {
            return "yaml";
        }

        String trimmed = content.trim();

        if (trimmed.startsWith("{")) {
            return "json";
        }

        if (trimmed.startsWith("---")) {
            return "yaml";
        }

        int colonCount = countOccurrences(trimmed, ':');
        int equalsCount = countOccurrences(trimmed, '=');
        int bracketCount = countOccurrences(trimmed, '[');
        int arrayStartCount = countOccurrences(trimmed, '[');

        // 检查 TOML 特征
        boolean hasTOMLAssignment = false;
        boolean hasArrayBracket = false;

        String[] lines = trimmed.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // 检查行内是否包含 TOML 赋值（key = "value"）
            int equalsIndex = line.indexOf('=');
            if (equalsIndex > 0) {
                // 检查等号前面是否是有效的键名（没有包含 : 或 { 等符号）
                String keyPart = line.substring(0, equalsIndex).trim();
                if (!keyPart.contains(":") && !keyPart.contains("{") && !keyPart.contains("[")) {
                    hasTOMLAssignment = true;
                }
            }

            // 检查数组开始标记
            if (line.endsWith(" = [") || line.contains(" = [")) {
                hasArrayBracket = true;
            }
        }

        // TOML 特征判断：有赋值语句且包含数组标记
        if (hasTOMLAssignment && hasArrayBracket) {
            return "toml";
        }

        // 检查 TOML section 标记
        if (trimmed.contains("[") && trimmed.contains("]")) {
            // 统计独立的 section 标记（不包含在 JSON 字符串中的）
            int sectionCount = 0;
            for (String line : lines) {
                line = line.trim();
                if (line.matches("^\\[.*\\]$")) {
                    sectionCount++;
                }
            }
            if (sectionCount > 0 && hasTOMLAssignment) {
                return "toml";
            }
        }

        if (colonCount > equalsCount && colonCount > bracketCount) {
            return "yaml";
        }

        return "yaml";
    }
    
    private int countOccurrences(String str, char c) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    /**
     * 更新进程配置
     * @param path 进程路径
     * @param configContext 配置内容
     * @param luaFiles Lua脚本文件列表
     * @return 错误信息，成功返回null
     */
    @Override
    public String updateProcess(String path, String configContext, ArrayList<LuaFile> luaFiles) {
        stopProcess(path);
        deleteProcess(path);
        return createProcess(path, configContext, luaFiles);
    }

    /**
     * 启动进程
     * @param path 进程路径
     * @return 进程ID
     */
    @Override
    public long startProcess(String path) {
        Process currentProcess = PROCESS_CACHE.get(path);
        if (currentProcess != null) {
            stopProcess(path);
        }

        String configPath = findConfigFile(path);
        if (configPath == null) {
            log.error("Config file not found for path: {}", path);
            return 0;
        }

        ProcessBuilder processBuilder = new ProcessBuilder(cmdPath, "-c", configPath);
        
        try {
            initLogFiles(path);
            Process process = processBuilder.start();
            PROCESS_CACHE.put(path, process);
            
            Path pidPath = Path.of(String.format(PID_FORMAT, taskWorkspace, path));
            Files.writeString(pidPath, String.valueOf(process.pid()), StandardOpenOption.CREATE);
            
            monitorService.monitorLog(LOG_INFO_FORMAT, taskWorkspace, path, process.getInputStream());
            monitorService.monitorLog(LOG_ERROR_FORMAT, taskWorkspace, path, process.getErrorStream());
            
            return process.pid();
            
        } catch (IOException e) {
            log.error("Failed to start process for path: {}", path, e);
        }
        return 0;
    }

    private void initLogFiles(String path) throws IOException {
        Path infoLogPath = Path.of(String.format(LOG_INFO_FORMAT, taskWorkspace, path));
        Path errorLogPath = Path.of(String.format(LOG_ERROR_FORMAT, taskWorkspace, path));
        Files.createDirectories(infoLogPath.getParent());
        createLogFileIfAbsent(infoLogPath);
        createLogFileIfAbsent(errorLogPath);
    }

    private void createLogFileIfAbsent(Path logPath) throws IOException {
        if (!Files.exists(logPath)) {
            Files.writeString(logPath, "create.......\n", StandardOpenOption.CREATE_NEW);
        }
    }
    
    private String findConfigFile(String path) {
        String[] extensions = {"yaml", "json", "toml"};
        for (String ext : extensions) {
            String configPath = String.format(CONFIG_FORMAT, taskWorkspace, path, ext);
            if (Files.exists(Path.of(configPath))) {
                return configPath;
            }
        }
        return null;
    }

    /**
     * 获取进程状态
     * @param path 进程路径
     * @return 状态：stopped/running/error
     */
    @Override
    public String status(String path) {
        Process process = PROCESS_CACHE.get(path);
        if (process == null) {
            return "stopped";
        } else if (process.isAlive()) {
            String errorLog = errorLog(path);
            if (errorLog != null && errorLog.contains(" ERROR ")) {
                return "running[error]";
            }
            return "running";
        } else {
            return "error";
        }
    }

    /**
     * 删除进程
     * @param path 进程路径
     * @return 是否成功
     */
    @Override
    public boolean deleteProcess(String path) {
        stopProcess(path);
        File taskDir = new File(taskWorkspace + File.separator + path);
        if (!taskDir.exists()) {
            return true;
        }
        return FileUtil.deleteDirectory(taskDir);
    }

    /**
     * 停止进程
     * @param path 进程路径
     * @return 是否成功
     */
    @Override
    public boolean stopProcess(String path) {
        Process process = PROCESS_CACHE.get(path);
        if (process == null) {
            return false;
        }
        
        try {
            process.destroy();
            boolean terminated = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!terminated) {
                process.destroyForcibly();
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            
            if (!process.isAlive()) {
                File pidFile = new File(String.format(PID_FORMAT, taskWorkspace, path));
                if (pidFile.exists() && !pidFile.delete()) {
                    log.warn("Failed to delete pid file: {}", pidFile.getAbsolutePath());
                }
                PROCESS_CACHE.remove(path);
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while stopping process: {}", path, e);
        }
        return false;
    }

    /**
     * 获取信息日志
     * @param path 进程路径
     * @return 日志内容
     */
    @Override
    public String infoLog(String path) {
        return readFile(String.format(LOG_INFO_FORMAT, taskWorkspace, path));
    }

    /**
     * 获取错误日志
     * @param path 进程路径
     * @return 日志内容
     */
    @Override
    public String errorLog(String path) {
        return readFile(String.format(LOG_ERROR_FORMAT, taskWorkspace, path));
    }

    /**
     * 读取文件内容
     * @param filePath 文件路径
     * @return 文件内容
     */
    private static String readFile(String filePath) {
        if (filePath == null) {
            return null;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return "日志文件尚未创建，请稍后重试。\n";
        }
        
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            log.debug("Failed to read file: {}", filePath, e);
        }
        return stringBuilder.toString();
    }

    /**
     * 检查进程是否存在
     * @param path 进程路径
     * @return 是否存在
     */
    public static boolean hasProcess(String path) {
        return PROCESS_CACHE.containsKey(path);
    }

    /**
     * 终止进程
     * @param processId 进程ID
     * @return 是否成功
     */
    private boolean cmdKill(String processId) {
        if (processId == null || processId.trim().isEmpty()) {
            return false;
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            String[] command;
            
            if (os.contains("win")) {
                command = new String[]{"taskkill", "/PID", processId, "/F"};
            } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
                command = new String[]{"kill", "-9", processId};
            } else {
                log.warn("Unsupported OS for process killing: {}", os);
                return false;
            }

            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            return exitCode == 0;

        } catch (Exception e) {
            log.debug("Failed to kill process: {}", processId, e);
        }
        return false;
    }
}
