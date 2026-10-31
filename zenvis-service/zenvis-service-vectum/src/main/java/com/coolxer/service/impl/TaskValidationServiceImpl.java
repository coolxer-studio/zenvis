package com.coolxer.service.impl;

import com.coolxer.model.validation.ConfigValidationResult;
import com.coolxer.model.validation.TestCaseResult;
import com.coolxer.model.validation.TestSummary;
import com.coolxer.model.validation.ValidationDiagnostic;
import com.coolxer.model.validation.ValidationRequest;
import com.coolxer.service.TaskValidationService;
import com.coolxer.utils.ConfigFormatDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 在隔离临时目录中执行 Vector 原生校验和单元测试。
 */
@Slf4j
@Service
public class TaskValidationServiceImpl implements TaskValidationService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 20;
    private static final int MAX_OUTPUT_BYTES = 24_000;
    private static final Pattern ENV_REFERENCE = Pattern.compile(
            "\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::[^}]*)?}");
    private static final Pattern TEST_RESULT = Pattern.compile(
            "(?m)^test (.+?) \\.\\.\\. (passed|failed)\\s*$");
    private static final Pattern TEST_DECLARATION = Pattern.compile(
            "(?m)^\\s*tests\\s*:|^\\s*\\[\\[tests]]|\"tests\"\\s*:");
    private static final Set<String> SUPPORTED_FORMATS = Set.of(
            "yaml", "yml", "json", "toml");

    private final String vectorHomePath;
    private final int timeoutSeconds;
    private volatile String vectorVersion;

    public TaskValidationServiceImpl(
            @Value("${vector.home}") String vectorHomePath,
            @Value("${vector.validation.timeout-seconds:20}") int timeoutSeconds) {
        this.vectorHomePath = vectorHomePath;
        this.timeoutSeconds = timeoutSeconds > 0
                ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * 校验完整 Vector 配置。
     *
     * @param request 校验请求
     * @return 隔离校验结果
     */
    @Override
    public ConfigValidationResult validate(ValidationRequest request) {
        String config = request == null ? "" : request.getConfig();
        String format = normalizeFormat(request == null ? null : request.getFormat(), config);
        ConfigValidationResult.ConfigValidationResultBuilder result =
                ConfigValidationResult.builder()
                        .format(format)
                        .candidateDigest(digest(config))
                        .vectorVersion(vectorVersion())
                        .normalizedConfig(config)
                        .diagnostics(new ArrayList<>());
        if (!StringUtils.hasText(config)) {
            result.passed(false).blocked(false);
            result.diagnostics(List.of(error(
                    "VECTOR_CONFIG_EMPTY", "$",
                    "Vector 配置不能为空。", "输出完整 YAML/TOML/JSON 配置。")));
            return result.build();
        }
        if (!SUPPORTED_FORMATS.contains(format)) {
            result.passed(false).blocked(false);
            result.diagnostics(List.of(error(
                    "VECTOR_FORMAT_UNSUPPORTED", "$format",
                    "不支持的 Vector 配置格式：" + format,
                    "使用 yaml、toml 或 json。")));
            return result.build();
        }

        Path vectorPath = Path.of(vectorHomePath, "bin", "vector");
        if (!Files.isExecutable(vectorPath)) {
            result.passed(false).blocked(true);
            result.diagnostics(List.of(error(
                    "VECTOR_VALIDATION_UNAVAILABLE", "$vector",
                    "Vectum 无法执行生产同版本 vector validate。",
                    "检查 vector.home 和 Vector 可执行文件权限后重试。")));
            return result.build();
        }

        Path temporaryDirectory = null;
        try {
            temporaryDirectory = Files.createTempDirectory("vectum-validation-");
            Path configPath = temporaryDirectory.resolve("push." + format);
            Files.writeString(configPath, config, StandardCharsets.UTF_8);

            CommandExecution validation = runVector(
                    List.of(vectorPath.toString(), "validate",
                            "--no-environment",
                            "--disable-env-var-interpolation",
                            configPath.toString()),
                    temporaryDirectory,
                    config);
            if (!validation.timedOut()
                    && validation.exitCode() != 0
                    && hasEnvironmentReference(config)
                    && looksLikeUninterpolatedEnvironmentFailure(validation.output())) {
                validation = runVector(
                        List.of(vectorPath.toString(), "validate", configPath.toString()),
                        temporaryDirectory,
                        config);
            }
            if (validation.timedOut()) {
                result.passed(false).blocked(true);
                result.diagnostics(List.of(error(
                        "VECTOR_VALIDATE_TIMEOUT", "$",
                        "Vector 配置校验超过 " + timeoutSeconds + " 秒。",
                        "检查配置是否触发网络或外部环境探测后重试。")));
                return result.build();
            }
            if (validation.exitCode() != 0) {
                result.passed(false).blocked(false);
                result.diagnostics(parseValidationDiagnostics(validation.output()));
                return result.build();
            }

            ConfigValidationResult nativeValidated = result.passed(true).build();
            TestSummary tests = runTests(
                    vectorPath, configPath, temporaryDirectory, config);
            nativeValidated.setTests(tests);
            if (tests.getFailed() > 0) {
                nativeValidated.setPassed(false);
                List<ValidationDiagnostic> diagnostics = new ArrayList<>(
                        nativeValidated.getDiagnostics());
                diagnostics.add(error(
                        "VECTOR_TEST_FAILED", "$tests",
                        tests.getCases().stream()
                                .filter(item -> !"passed".equals(item.getStatus()))
                                .map(TestCaseResult::getMessage)
                                .filter(StringUtils::hasText)
                                .findFirst()
                                .orElse("Vector 单元测试未通过。"),
                        "修正断言或 Transform 输出，使合法样例和异常/死信样例均通过。"));
                nativeValidated.setDiagnostics(diagnostics);
            } else if (request != null && request.isTestsRequired()
                    && (!tests.isPresent() || tests.getTotal() == 0)) {
                nativeValidated.setPassed(false);
                List<ValidationDiagnostic> diagnostics = new ArrayList<>(
                        nativeValidated.getDiagnostics());
                diagnostics.add(error(
                        "VECTOR_TESTS_REQUIRED", "$tests",
                        "包含 Transform 或 ClickHouse 写入的配置必须提供 Vector 单元测试。",
                        "至少增加一个合法样例测试和一个异常/死信测试。"));
                nativeValidated.setDiagnostics(diagnostics);
            }
            return nativeValidated;
        } catch (IOException e) {
            result.passed(false).blocked(true);
            result.diagnostics(List.of(error(
                    "VECTOR_VALIDATION_IO_ERROR", "$",
                    "创建隔离校验目录失败：" + e.getMessage(),
                    "检查 Vectum 临时目录权限和磁盘空间。")))
                    ;
            return result.build();
        } finally {
            deleteDirectory(temporaryDirectory);
        }
    }

    private TestSummary runTests(Path vectorPath,
                                 Path configPath,
                                 Path workingDirectory,
                                 String config) throws IOException {
        if (!TEST_DECLARATION.matcher(config).find()) {
            return TestSummary.builder().present(false).build();
        }
        CommandExecution execution = runVector(
                List.of(vectorPath.toString(), "test", configPath.toString()),
                workingDirectory,
                config);
        if (execution.timedOut()) {
            return TestSummary.builder()
                    .present(true)
                    .total(1)
                    .passed(0)
                    .failed(1)
                    .cases(List.of(TestCaseResult.builder()
                            .name("vector test")
                            .status("failed")
                            .message("测试执行超时")
                            .build()))
                    .build();
        }
        TestSummary summary = parseTestSummary(execution.output());
        if (execution.exitCode() != 0 && summary.getFailed() == 0) {
            List<TestCaseResult> cases = new ArrayList<>(summary.getCases());
            cases.add(TestCaseResult.builder()
                    .name("vector test")
                    .status("failed")
                    .message(abbreviate(execution.output(), 2_000))
                    .build());
            return TestSummary.builder()
                    .present(true)
                    .total(cases.size())
                    .passed(summary.getPassed())
                    .failed(Math.max(1, summary.getFailed()))
                    .cases(cases)
                    .build();
        }
        return summary;
    }

    private TestSummary parseTestSummary(String output) {
        List<TestCaseResult> cases = new ArrayList<>();
        Matcher matcher = TEST_RESULT.matcher(output == null ? "" : output);
        int passed = 0;
        int failed = 0;
        while (matcher.find()) {
            String status = matcher.group(2);
            if ("passed".equals(status)) {
                passed++;
            } else {
                failed++;
            }
            cases.add(TestCaseResult.builder()
                    .name(matcher.group(1).trim())
                    .status(status)
                    .build());
        }
        boolean noTests = output != null && output.contains("No tests found.");
        boolean present = !cases.isEmpty() || !noTests;
        if (failed > 0) {
            for (int index = 0; index < cases.size(); index++) {
                TestCaseResult item = cases.get(index);
                if ("failed".equals(item.getStatus())) {
                    item.setMessage(abbreviate(output, 2_000));
                }
            }
        }
        return TestSummary.builder()
                .present(present)
                .total(cases.size())
                .passed(passed)
                .failed(failed)
                .cases(cases)
                .build();
    }

    private List<ValidationDiagnostic> parseValidationDiagnostics(String output) {
        String text = output == null ? "" : output.trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        List<ValidationDiagnostic> diagnostics = new ArrayList<>();
        if (normalized.contains("missing environment variable")) {
            diagnostics.add(error(
                    "VECTOR_ENV_MISSING", "$environment",
                    text, "补齐运行环境变量，或在配置中提供安全的非敏感默认值。"));
        } else if (normalized.contains("invalid type: sequence")
                || normalized.contains("expected a map")) {
            diagnostics.add(error(
                    "VECTOR_SCHEMA_ARRAY_AS_MAP", "$",
                    text, "sources、transforms、sinks 必须使用以组件 ID 为键的映射，不能写成数组。"));
        } else if (normalized.contains("unknown field")) {
            diagnostics.add(error(
                    "VECTOR_UNKNOWN_FIELD", "$",
                    text, "删除 Vector 当前版本不支持的字段，并参考同版本组件 schema 修改。"));
        } else if (normalized.contains("failed to compile")
                || normalized.contains("error[e")
                || normalized.contains("vrl")) {
            diagnostics.add(error(
                    "VECTOR_VRL_ERROR", "$transforms",
                    text, "根据错误行修复 VRL，并在提交前保留合法样例与异常样例测试。"));
        } else if (normalized.contains("test") && normalized.contains("failed")) {
            diagnostics.add(error(
                    "VECTOR_TEST_FAILED", "$tests",
                    text, "修正断言或 Transform 输出，使合法样例和异常/死信样例均通过。"));
        } else if (normalized.contains("invalid uri character")) {
            diagnostics.add(error(
                    "VECTOR_ENV_PLACEHOLDER_INVALID", "$",
                    text, "运行环境变量必须提供可解析的地址、认证和目标值。"));
        } else {
            diagnostics.add(error(
                    "VECTOR_VALIDATE_FAILED", "$",
                    text, "按 Vector 错误信息修复完整配置后重新校验。"));
        }
        return diagnostics;
    }

    private CommandExecution runVector(List<String> command,
                                       Path workingDirectory,
                                       String config) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true);
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile());
        }
        applyPlaceholderEnvironment(builder.environment(), config);
        Process process = builder.start();
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(
                () -> readBounded(process.getInputStream()));
        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return new CommandExecution(-1, true, outputFuture.getNow(""));
            }
            return new CommandExecution(process.exitValue(), false,
                    outputFuture.get(2, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new CommandExecution(-1, true, outputFuture.getNow(""));
        } catch (Exception e) {
            process.destroyForcibly();
            log.warn("Vector command collection failed: command={}, error={}", command, e.getMessage());
            return new CommandExecution(-1, false, outputFuture.getNow(""));
        }
    }

    private String readBounded(InputStream inputStream) {
        StringBuilder result = new StringBuilder();
        byte[] buffer = new byte[4096];
        int read;
        try (InputStream input = inputStream) {
            while ((read = input.read(buffer)) != -1) {
                if (result.length() < MAX_OUTPUT_BYTES) {
                    int accepted = Math.min(read, MAX_OUTPUT_BYTES - result.length());
                    result.append(new String(buffer, 0, accepted, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            log.debug("Vector command output stream closed: {}", e.getMessage());
        }
        return result.toString();
    }

    private void applyPlaceholderEnvironment(Map<String, String> environment, String config) {
        Matcher matcher = ENV_REFERENCE.matcher(config == null ? "" : config);
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        for (String name : names) {
            environment.putIfAbsent(name, placeholderFor(name));
        }
    }

    private String placeholderFor(String name) {
        String normalized = name.toUpperCase(Locale.ROOT);
        if (normalized.contains("ENDPOINT") || normalized.contains("URL")
                || normalized.contains("URI")) {
            return "http://validation.invalid:1";
        }
        if (normalized.contains("BOOTSTRAP") || normalized.contains("BROKER")) {
            return "validation.invalid:9092";
        }
        if (normalized.contains("TOPIC") || normalized.contains("GROUP")) {
            return "validation-topic";
        }
        return "validation-placeholder";
    }

    private boolean looksLikeUninterpolatedEnvironmentFailure(String output) {
        String normalized = output == null ? "" : output.toLowerCase(Locale.ROOT);
        return normalized.contains("invalid uri")
                || normalized.contains("$")
                || normalized.contains("environment variable");
    }

    private boolean hasEnvironmentReference(String config) {
        return config != null && ENV_REFERENCE.matcher(config).find();
    }

    private String vectorVersion() {
        if (vectorVersion != null) {
            return vectorVersion;
        }
        Path vectorPath = Path.of(vectorHomePath, "bin", "vector");
        if (!Files.isExecutable(vectorPath)) {
            vectorVersion = "unavailable";
            return vectorVersion;
        }
        try {
            CommandExecution execution = runVector(
                    List.of(vectorPath.toString(), "--version"), null, "");
            vectorVersion = execution.output().trim();
        } catch (IOException e) {
            vectorVersion = "unavailable";
            log.warn("Failed to read Vector version: {}", e.getMessage());
        }
        return vectorVersion;
    }

    private String normalizeFormat(String requested, String config) {
        return ConfigFormatDetector.detect(requested, config);
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private ValidationDiagnostic error(String code,
                                       String path,
                                       String message,
                                       String repairHint) {
        return ValidationDiagnostic.builder()
                .code(code)
                .severity("ERROR")
                .source("VECTOR")
                .path(path)
                .message(abbreviate(message, 8_000))
                .repairHint(repairHint)
                .build();
    }

    private String abbreviate(String value, int limit) {
        String text = value == null ? "" : value;
        return text.length() <= limit ? text : text.substring(0, limit) + "…";
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Failed to delete validation temporary path: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean validation temporary directory: {}", directory, e);
        }
    }

    private record CommandExecution(int exitCode, boolean timedOut, String output) {
    }
}
