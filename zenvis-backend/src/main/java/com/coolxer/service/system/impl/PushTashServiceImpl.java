package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.system.PushTaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 *
 */
@Slf4j
@Service
public class PushTashServiceImpl implements PushTaskService {


    @Autowired
    private CustomWebConfig customWebConfig;

    @Autowired
    private RestTemplate restTemplate;

    public Object proxy(HttpServletRequest request) {
        try {
            String queryString = request.getQueryString();
            String targetUrl = customWebConfig.getDataServiceUrl() + request.getRequestURI().replace("/api/v1/system/push-task", "/vectum/api/v1/task");
            if (queryString != null && !queryString.isEmpty()) {
                targetUrl += "?" + queryString;
            }

            HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod());

            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.set(headerName, request.getHeader(headerName));
            }
            applyVectumAuthorization(headers);

            HttpEntity<byte[]> entity = new HttpEntity<>(request.getInputStream().readAllBytes(), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    targetUrl,
                    httpMethod,
                    entity,
                    String.class
            );
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                return ResponseWrap.fail(ResultCodeEnum.UNKNOWN_ERROR);
            }

        } catch (Exception e) {
            log.error("代理转发失败: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    public boolean createAndStart(PushTaskDto pushTaskDto) {
        ResponseModel responseForCreate = restTemplate.exchange(
                customWebConfig.getDataServiceUrl() + "/vectum/api/v1/task/add",
                HttpMethod.POST,
                new HttpEntity<>(pushTaskDto, createVectumHeaders()),
                ResponseModel.class
        ).getBody();
        if (responseForCreate != null && responseForCreate.succeed()) {
            PushTaskVo createdTask = JacksonConfig.OBJECT_MAPPER.convertValue(responseForCreate.getData(), PushTaskVo.class);
            if (Objects.nonNull(createdTask.getId())) {
                ResponseModel responseForStart = restTemplate.exchange(
                        customWebConfig.getDataServiceUrl() + "/vectum/api/v1/task/" + createdTask.getId() + "/toggle",
                        HttpMethod.POST,
                        new HttpEntity<>(createVectumHeaders()),
                        ResponseModel.class
                ).getBody();
                if (responseForStart != null && responseForStart.succeed()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean updateAndStart(Integer id, PushTaskDto pushTaskDto) {
        return update(id, pushTaskDto) && toggle(id);
    }

    @Override
    public boolean update(Integer id, PushTaskDto pushTaskDto) {
        ResponseModel response = restTemplate.exchange(
                customWebConfig.getDataServiceUrl() + "/vectum/api/v1/task/{id}",
                HttpMethod.PUT,
                new HttpEntity<>(pushTaskDto, createVectumHeaders()),
                ResponseModel.class,
                id
        ).getBody();
        return response != null && response.succeed();
    }

    @Override
    public boolean toggle(Integer id) {
        ResponseModel response = restTemplate.exchange(
                customWebConfig.getDataServiceUrl() + "/vectum/api/v1/task/{id}/toggle",
                HttpMethod.POST,
                new HttpEntity<>(createVectumHeaders()),
                ResponseModel.class,
                id
        ).getBody();
        return response != null && response.succeed();
    }

    @Override
    public boolean delete(Integer id) {
        ResponseModel response = restTemplate.exchange(
                customWebConfig.getDataServiceUrl() + "/vectum/api/v1/task/{id}",
                HttpMethod.DELETE,
                new HttpEntity<>(createVectumHeaders()),
                ResponseModel.class,
                id
        ).getBody();
        return response != null && response.succeed();
    }

    @Override
    public List<PushTaskVo> findAll() {
        ResponseModel response = restTemplate.exchange(
                customWebConfig.getDataServiceUrl() + "/vectum/api/v1/task/all",
                HttpMethod.GET,
                new HttpEntity<>(createVectumHeaders()),
                ResponseModel.class
        ).getBody();
        if (response != null && response.succeed()) {
            return JacksonConfig.OBJECT_MAPPER.convertValue(response.getData(),
                    JacksonConfig.OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, PushTaskVo.class));
        }
        throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR.getCode(), "数据推送服务返回失败");
    }

    @Override
    public PushTaskVo findById(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("数据推送任务 ID 不能为空");
        }
        ResponseModel response = restTemplate.exchange(
                customWebConfig.getDataServiceUrl() + "/vectum/api/v1/task/{id}/view",
                HttpMethod.GET,
                new HttpEntity<>(createVectumHeaders()),
                ResponseModel.class,
                id
        ).getBody();
        if (response != null && response.succeed() && response.getData() != null) {
            return JacksonConfig.OBJECT_MAPPER.convertValue(response.getData(), PushTaskVo.class);
        }
        String message = response == null || !StringUtils.hasText(response.getMsg())
                ? "查询数据推送任务详情失败"
                : response.getMsg();
        throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR.getCode(), message);
    }

    @Override
    public List<PushTaskVo> findBySourceMark(String sourceMark) {
        return findAll().stream()
                .filter(pushTaskVo -> "SYSTEM".equals(pushTaskVo.getSource()))
                .filter(pushTaskVo -> Objects.equals(pushTaskVo.getMark(), sourceMark))
                .toList();
    }

    @Override
    public boolean deleteBySourceMark(String sourceMark) {
        List<PushTaskVo> pushTaskList = findBySourceMark(sourceMark);
        boolean succeeded = true;
        for (PushTaskVo pushTask : pushTaskList) {
            succeeded = delete(pushTask.getId()) && succeeded;
        }
        return succeeded;
    }

    @Override
    public String getLog(Integer id, String logType) {
        if (id == null) {
            throw new IllegalArgumentException("数据推送任务 ID 不能为空");
        }
        if (!"console".equals(logType) && !"system".equals(logType)) {
            throw new IllegalArgumentException("日志类型仅支持 console 或 system");
        }
        String responseBody = restTemplate.exchange(
                customWebConfig.getDataServiceUrl()
                        + "/vectum/api/v1/task/{id}/log?log_type={logType}",
                HttpMethod.GET,
                new HttpEntity<>(createVectumHeaders()),
                String.class,
                id,
                logType
        ).getBody();
        if (responseBody == null) {
            throw new ApiException(
                    ResultCodeEnum.UNKNOWN_ERROR.getCode(),
                    "Vectum 未返回数据推送任务日志"
            );
        }
        return unwrapLogResponse(responseBody);
    }

    private String unwrapLogResponse(String responseBody) {
        try {
            JsonNode payload = JacksonConfig.OBJECT_MAPPER.readTree(responseBody);
            if (payload == null || !payload.isObject() || !payload.has("status")) {
                return responseBody;
            }
            if (payload.path("status").asInt(Integer.MIN_VALUE) != 0) {
                String message = payload.path("msg").asText();
                throw new ApiException(
                        ResultCodeEnum.UNKNOWN_ERROR.getCode(),
                        StringUtils.hasText(message) ? message : "获取数据推送任务日志失败"
                );
            }
            JsonNode data = payload.get("data");
            if (data == null || data.isNull()) {
                throw new ApiException(
                        ResultCodeEnum.UNKNOWN_ERROR.getCode(),
                        "Vectum 未返回数据推送任务日志"
                );
            }
            return data.isTextual() ? data.asText() : data.toString();
        } catch (JsonProcessingException ignored) {
            return responseBody;
        }
    }

    private HttpHeaders createVectumHeaders() {
        HttpHeaders headers = new HttpHeaders();
        applyVectumAuthorization(headers);
        return headers;
    }

    private void applyVectumAuthorization(HttpHeaders headers) {
        headers.remove(HttpHeaders.AUTHORIZATION);
        String bearerToken = customWebConfig.getDataServiceBearerToken();
        if (StringUtils.hasText(bearerToken)) {
            headers.setBearerAuth(bearerToken.trim());
        }
    }

    @Override
    public String detectFormat(String content) {
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

    @Data
    static class RequestPushTask {

        private String path;
        private String config;
        private ArrayList<LuaFile> luaFiles;

        @Data
        @AllArgsConstructor
        static public class LuaFile {

            private String fileName;
            private String context;

        }
    }

    @Data
    static class ResponseModel {

        private static final Integer CODE_SUCCEED = 0;
        private static final Integer CODE_FAILED = 1;

        /**
         * 响应结果代码
         */
        private Integer status;

        /**
         * 提示消息(msg 是 message 的缩写，使用缩写是为了兼容原来的代码)
         */
        private String msg;

        /**
         * 数据
         */
        private Object data;

        public ResponseModel() {
        }

        public ResponseModel(Integer status, String msg, Object data) {
            this.status = status;
            this.msg = msg;
            this.data = data;
        }

        public boolean succeed() {
            return (CODE_SUCCEED.equals(this.status));
        }

        public boolean failed() {
            return !succeed();
        }

    }

}
