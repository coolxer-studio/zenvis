package com.coolxer.service.system;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.system.dto.PushTaskValidationRequest;
import com.coolxer.model.system.validation.PushTaskDiagnosticResultVo;
import com.coolxer.model.system.validation.PushTaskDiagnosticVo;
import com.coolxer.model.system.validation.PushTaskTestSummaryVo;
import com.coolxer.model.system.validation.PushTaskValidationVo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 合并平台静态规则和 Vectum 原生校验结果的配置门禁服务。
 */
@Slf4j
@Service
public class PushTaskConfigValidationService {

    private final PushTaskConfigRules configRules;
    private final CustomWebConfig customWebConfig;
    private final RestTemplate restTemplate;

    public PushTaskConfigValidationService(PushTaskConfigRules configRules,
                                           CustomWebConfig customWebConfig,
                                           RestTemplate restTemplate) {
        this.configRules = configRules;
        this.customWebConfig = customWebConfig;
        this.restTemplate = restTemplate;
    }

    /**
     * 校验候选配置并合并两类诊断。
     *
     * @param request 校验请求
     * @return 统一校验结果
     */
    public PushTaskValidationVo validate(PushTaskValidationRequest request) {
        String config = request == null ? "" : request.getConfig();
        String metaContent = request == null ? "" : request.getMetaContent();
        PushTaskConfigRules.StaticValidation staticValidation = configRules.validate(
                config, request == null ? null : request.getFormat(), metaContent);
        List<PushTaskDiagnosticVo> diagnostics = new ArrayList<>(
                staticValidation.diagnostics());
        if (!Set.of("yaml", "toml", "json").contains(staticValidation.format())) {
            PushTaskValidationVo result = PushTaskValidationVo.builder()
                    .passed(false)
                    .blocked(false)
                    .format(staticValidation.format())
                    .candidateDigest(digest(config))
                    .metaDigest(digest(metaContent))
                    .vectorVersion("unavailable")
                    .diagnostics(diagnostics)
                    .tests(new PushTaskTestSummaryVo())
                    .normalizedConfig(config)
                    .build();
            result.setValidationDigest(validationDigest(result));
            return result;
        }
        PushTaskValidationVo nativeResult;
        try {
            nativeResult = callVectumValidate(
                    config, staticValidation.format(), staticValidation.testsRequired());
        } catch (RuntimeException e) {
            diagnostics.add(diagnostic(
                    "PUSH_VALIDATION_UNAVAILABLE", "VECTUM", "$",
                    "Vectum 原生校验不可用：" + safeMessage(e),
                    "检查 Vectum 服务和 Vector 可执行文件；校验不可用时禁止创建或修复。"));
            return PushTaskValidationVo.builder()
                    .passed(false)
                    .blocked(true)
                    .format(staticValidation.format())
                    .candidateDigest(digest(config))
                    .metaDigest(digest(metaContent))
                    .vectorVersion("unavailable")
                    .diagnostics(diagnostics)
                    .tests(new PushTaskTestSummaryVo())
                    .normalizedConfig(config)
                    .build();
        }

        diagnostics.addAll(nativeResult.getDiagnostics() == null
                ? List.of() : nativeResult.getDiagnostics());
        String expectedDigest = digest(config);
        boolean digestMatched = StringUtils.hasText(nativeResult.getCandidateDigest())
                && expectedDigest.equals(nativeResult.getCandidateDigest());
        if (!digestMatched) {
            diagnostics.add(diagnostic(
                    "PUSH_VALIDATION_DIGEST_MISMATCH", "VECTUM", "$",
                    StringUtils.hasText(nativeResult.getCandidateDigest())
                            ? "Vectum 返回的候选摘要与提交配置不一致。"
                            : "Vectum 未返回候选摘要，无法证明校验对象与提交配置一致。",
                    "停止使用缓存校验结果，重新提交完整配置。"));
        }
        boolean passed = staticValidation.passed()
                && nativeResult.isPassed()
                && !nativeResult.isBlocked()
                && digestMatched;
        PushTaskValidationVo result = PushTaskValidationVo.builder()
                .passed(passed)
                .blocked(nativeResult.isBlocked())
                .format(firstText(nativeResult.getFormat(), staticValidation.format()))
                .candidateDigest(expectedDigest)
                .metaDigest(digest(metaContent))
                .vectorVersion(nativeResult.getVectorVersion())
                .diagnostics(diagnostics)
                .tests(nativeResult.getTests() == null
                        ? new PushTaskTestSummaryVo() : nativeResult.getTests())
                .normalizedConfig(firstText(nativeResult.getNormalizedConfig(), config))
                .build();
        result.setValidationDigest(validationDigest(result));
        return result;
    }

    /**
     * 读取 Vectum 当前运行轮次诊断。
     *
     * @param taskId 任务 ID
     * @return 结构化诊断
     */
    public PushTaskDiagnosticResultVo diagnose(Integer taskId) {
        try {
            String url = customWebConfig.getDataServiceUrl()
                    + "/vectum/api/v1/task/" + taskId + "/diagnostics";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(vectumHeaders()), String.class);
            JsonNode data = responseData(response.getBody());
            try {
                return JacksonConfig.OBJECT_MAPPER.treeToValue(
                        data, PushTaskDiagnosticResultVo.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("Vectum 诊断响应无法解析", e);
            }
        } catch (RuntimeException e) {
            log.warn("读取 Vectum 运行诊断失败: taskId={}, error={}", taskId, e.getMessage());
            return PushTaskDiagnosticResultVo.builder()
                    .verdict("UNAVAILABLE")
                    .taskStatus("unknown")
                    .failureStage("DIAGNOSTICS_UNAVAILABLE")
                    .repairable(false)
                    .diagnostics(List.of(diagnostic(
                            "PUSH_DIAGNOSTICS_UNAVAILABLE", "VECTUM", "$",
                            "Vectum 当前轮次诊断不可用：" + safeMessage(e),
                            "恢复 Vectum 后重新诊断；不得把不可用视为运行成功。")))
                    .build();
        }
    }

    private PushTaskValidationVo callVectumValidate(String config,
                                                    String format,
                                                    boolean testsRequired) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("config", config);
        body.put("format", format);
        body.put("testsRequired", testsRequired);
        String url = customWebConfig.getDataServiceUrl()
                + "/vectum/api/v1/task/validate";
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(body, vectumHeaders()), String.class);
        JsonNode data = responseData(response.getBody());
        try {
            return JacksonConfig.OBJECT_MAPPER.treeToValue(data, PushTaskValidationVo.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Vectum 校验响应无法解析", e);
        }
    }

    private JsonNode responseData(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("Vectum 返回空响应");
        }
        JsonNode root;
        try {
            root = JacksonConfig.OBJECT_MAPPER.readTree(responseBody);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Vectum 响应不是合法 JSON", e);
        }
        int status = root.path("status").asInt(1);
        if (status != 0) {
            throw new IllegalStateException(firstText(root.path("msg").asText(""), "Vectum 请求失败"));
        }
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            return JacksonConfig.OBJECT_MAPPER.createObjectNode();
        }
        return data;
    }

    private HttpHeaders vectumHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String token = customWebConfig.getDataServiceBearerToken();
        if (StringUtils.hasText(token)) {
            headers.setBearerAuth(token.trim());
        }
        return headers;
    }

    private String validationDigest(PushTaskValidationVo result) {
        return digest(firstText(result.getCandidateDigest(), "")
                + ":" + firstText(result.getMetaDigest(), "")
                + ":" + result.isPassed()
                + ":" + firstText(result.getVectorVersion(), "")
                + ":" + result.getTests().getPassed()
                + ":" + result.getTests().getFailed());
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private PushTaskDiagnosticVo diagnostic(String code,
                                            String source,
                                            String path,
                                            String message,
                                            String repairHint) {
        return PushTaskDiagnosticVo.builder()
                .code(code)
                .severity("ERROR")
                .source(source)
                .path(path)
                .message(message)
                .repairHint(repairHint)
                .build();
    }

    private String safeMessage(Throwable error) {
        return error == null || !StringUtils.hasText(error.getMessage())
                ? "未知错误" : error.getMessage();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
