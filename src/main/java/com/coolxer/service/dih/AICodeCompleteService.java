package com.coolxer.service.dih;

import com.coolxer.service.dih.logging.LlmLogHelper;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI自动补全提示服务（针对代码）
 */
@Service
public class AICodeCompleteService {
    private static final Logger log = LoggerFactory.getLogger(AICodeCompleteService.class);

    @Value("${spring.ai.openai.completion.url:https://api.openai.com/v1/completions}")
    private String apiUrl;
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;
    @Value("${spring.ai.openai.completion.options.model:}")
    private String completionModel;

    /**
     * 调用 OpenAI API 完成代码补全
     *
     * @param prompt 代码提示
     * @return API响应结果
     * @throws Exception 网络或IO异常
     */
    public String completeCode(String prompt) throws Exception {
        String scene = "AICodeCompleteService.completeCode";
        String requestId = LlmLogHelper.newRequestId();
        long startedAtNanos = System.nanoTime();
        if (completionModel == null || completionModel.isBlank()) {
            throw new IllegalStateException("OpenAI completion model is not configured.");
        }
        // 构建请求JSON数据
        String jsonInputString = JacksonUtil.toJson(new CompletionParams(completionModel, prompt));
        LlmLogHelper.logRequest(log, requestId, scene, buildLogRequest(prompt));
        // 创建URL连接
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        String responseBody = null;

        try {
            // 设置请求方法和头部
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // 发送请求体
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 读取响应
            int responseCode = connection.getResponseCode();
            String jsonString;
            if (responseCode == HttpURLConnection.HTTP_OK) {
                jsonString = readResponseBody(connection.getInputStream());
                responseBody = jsonString;
                ObjectMapper objectMapper = new ObjectMapper();
                // 解析 JSON 字符串为 JsonNode
                JsonNode rootNode = objectMapper.readTree(jsonString);
                // 获取 choices 数组
                JsonNode choicesNode = rootNode.path("choices");
                // 检查 choices 是否为空
                if (choicesNode.isArray() && choicesNode.size() > 0) {
                    // 获取 choices 数组中的第一个对象
                    JsonNode firstChoiceNode = choicesNode.get(0);
                    // 获取 text 字段
                    JsonNode textNode = firstChoiceNode.path("text");
                    // 检查 text 字段是否存在
                    if (textNode.isTextual()) {
                        String textValue = textNode.asText();
                        LlmLogHelper.logResponse(log, requestId, scene,
                                buildLogResponse(responseCode, jsonString, textValue), startedAtNanos);
                        return textValue;
                    } else {
                        throw new RuntimeException("Text field is not a string or does not exist.");
                    }
                } else {
                    throw new RuntimeException("Choices array is empty or does not exist.");
                }
            } else {
                jsonString = readResponseBody(connection.getErrorStream());
                responseBody = jsonString;
                LlmLogHelper.logResponse(log, requestId, scene,
                        buildLogResponse(responseCode, jsonString, null), startedAtNanos);
                throw new RuntimeException("OpenAI API调用失败，响应码: " + responseCode);
            }
        } catch (Exception e) {
            LlmLogHelper.logError(log, requestId, scene, responseBody, startedAtNanos, e);
            throw e;
        }
    }

    // 使用示例
    public String completeCode(String prefixContent, String suffixContent) {
        String promptTemplate = "<|fim_prefix|>%s<|fim_suffix|>%s<|fim_middle|>";
        String prompt = promptTemplate.formatted(prefixContent, suffixContent);
        try {
            String result = completeCode(prompt);
            return result;
        } catch (Exception e) {
            log.error("OpenAI code completion failed.", e);
        }
        return null;
    }

    private String readResponseBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }

    private Map<String, Object> buildLogRequest(String prompt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("url", apiUrl);
        request.put("model", completionModel);
        request.put("prompt", prompt);
        return request;
    }

    private Map<String, Object> buildLogResponse(int responseCode, String body, String completionText) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status_code", responseCode);
        response.put("body", body);
        response.put("completion_text", completionText);
        return response;
    }

    @Data
    @AllArgsConstructor
    static class CompletionParams {
        private String model;
        private String prompt;
    }
}
