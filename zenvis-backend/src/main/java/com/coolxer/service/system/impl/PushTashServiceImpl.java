package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.dto.PushTaskQueryDto;
import com.coolxer.model.system.dto.PushTaskValidationRequest;
import com.coolxer.model.system.validation.PushTaskDiagnosticResultVo;
import com.coolxer.model.system.validation.PushTaskDiagnosticVo;
import com.coolxer.model.system.validation.PushTaskValidationVo;
import com.coolxer.model.system.vo.PushTaskOperationVo;
import com.coolxer.model.system.vo.PushTaskPageVo;
import com.coolxer.model.system.vo.PushTaskSummaryVo;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.system.PushTaskConfigValidationService;
import com.coolxer.service.system.PushTaskConfigException;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

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

    @Autowired
    private PushTaskConfigValidationService validationService;

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

            byte[] body = request.getInputStream().readAllBytes();
            enforceProxyValidation(request, body);
            HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);

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

        } catch (ApiException | IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("代理转发失败: {}", e.getMessage(), e);
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR);
        }
    }

    @Override
    public boolean createAndStart(PushTaskDto pushTaskDto) {
        try {
            return createAndStartTask(pushTaskDto).getTaskId() != null;
        } catch (PushTaskConfigException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("创建并启动推送任务失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean createAndStartForPlugin(PushTaskDto pushTaskDto) {
        return createAndStartInternalTask(pushTaskDto).getTaskId() != null;
    }

    @Override
    public PushTaskOperationVo createAndStartTask(PushTaskDto pushTaskDto) {
        requireValidation(pushTaskDto);
        return createAndStartInternalTask(pushTaskDto);
    }

    private PushTaskOperationVo createAndStartInternalTask(PushTaskDto pushTaskDto) {
        if (!StringUtils.hasText(pushTaskDto.getFormat())) {
            throw new PushTaskConfigException("创建推送任务时必须显式指定 format");
        }
        ResponseModel responseForCreate = restTemplate.exchange(
                customWebConfig.getDataServiceUrl() + "/vectum/api/v1/task/add",
                HttpMethod.POST,
                new HttpEntity<>(pushTaskDto, createVectumHeaders()),
                ResponseModel.class
        ).getBody();
        requireSucceeded(responseForCreate, "创建数据推送任务失败");
        PushTaskVo createdTask = JacksonConfig.OBJECT_MAPPER.convertValue(
                responseForCreate.getData(), PushTaskVo.class);
        if (createdTask == null || createdTask.getId() == null) {
            throw new IllegalStateException("Vectum 创建任务后未返回 taskId");
        }
        ResponseModel responseForStart = restTemplate.exchange(
                customWebConfig.getDataServiceUrl() + "/vectum/api/v1/task/{id}/start",
                HttpMethod.POST,
                new HttpEntity<>(createVectumHeaders()),
                ResponseModel.class,
                createdTask.getId()
        ).getBody();
        requireSucceeded(responseForStart, "创建后启动数据推送任务失败");
        PushTaskVo task = findById(createdTask.getId());
        return operation("create_and_start", null, task, true);
    }

    @Override
    public boolean updateAndStart(Integer id, PushTaskDto pushTaskDto) {
        try {
            return updateAndRestartTask(id, pushTaskDto).getTaskId() != null;
        } catch (PushTaskConfigException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("更新并重启推送任务失败: taskId={}, error={}", id, e.getMessage());
            return false;
        }
    }

    @Override
    public PushTaskOperationVo updateAndRestartTask(Integer id, PushTaskDto pushTaskDto) {
        requireValidation(pushTaskDto);
        PushTaskVo previous = findById(id);
        if (!updateInternal(id, pushTaskDto)) {
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR.getCode(),
                    "更新数据推送任务失败: taskId=" + id);
        }
        return operationRequest("update_and_restart", id, "restart", previous.getStatus());
    }

    /**
     * 校验完整推送配置。
     *
     * @param request 校验请求
     * @return 统一校验结果
     */
    @Override
    public PushTaskValidationVo validateConfig(PushTaskValidationRequest request) {
        return validationService.validate(request);
    }

    /**
     * 获取任务当前运行轮次诊断。
     *
     * @param id 任务 ID
     * @return 结构化诊断
     */
    @Override
    public PushTaskDiagnosticResultVo diagnose(Integer id) {
        return validationService.diagnose(id);
    }

    @Override
    public boolean update(Integer id, PushTaskDto pushTaskDto) {
        requireValidation(pushTaskDto);
        return updateInternal(id, pushTaskDto);
    }

    @Override
    public boolean updateForPlugin(Integer id, PushTaskDto pushTaskDto) {
        return updateInternal(id, pushTaskDto);
    }

    private boolean updateInternal(Integer id, PushTaskDto pushTaskDto) {
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
    public PushTaskOperationVo startTask(Integer id) {
        PushTaskVo previous = findById(id);
        return operationRequest("start", id, "start", previous.getStatus());
    }

    @Override
    public PushTaskOperationVo stopTask(Integer id) {
        PushTaskVo previous = findById(id);
        return operationRequest("stop", id, "stop", previous.getStatus());
    }

    @Override
    public PushTaskOperationVo restartTask(Integer id) {
        PushTaskVo previous = findById(id);
        return operationRequest("restart", id, "restart", previous.getStatus());
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
    public PushTaskOperationVo deleteTask(Integer id) {
        PushTaskVo previous = findById(id);
        if (!delete(id)) {
            throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR.getCode(),
                    "删除数据推送任务失败: taskId=" + id);
        }
        return PushTaskOperationVo.builder()
                .action("delete")
                .taskId(id)
                .previousStatus(previous.getStatus())
                .status("deleted")
                .changed(true)
                .pid(0)
                .currentRunId(previous.getCurrentRunId())
                .runStartedAt(previous.getRunStartedAt())
                .build();
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
    public PushTaskPageVo query(PushTaskQueryDto query) {
        PushTaskQueryDto safeQuery = query == null ? new PushTaskQueryDto() : query;
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(customWebConfig.getDataServiceUrl()
                        + "/vectum/api/v1/task/query");
        queryParam(builder, "source", safeQuery.getSource());
        queryParam(builder, "mark", safeQuery.getSourceMark());
        queryParam(builder, "nameContains", safeQuery.getNameContains());
        queryParam(builder, "status", safeQuery.getStatus());
        builder.queryParam("offset", safeQuery.getOffset() == null ? 0 : safeQuery.getOffset());
        builder.queryParam("limit", safeQuery.getLimit() == null ? 20 : safeQuery.getLimit());
        ResponseModel response = restTemplate.exchange(
                builder.build(true).toUri(),
                HttpMethod.GET,
                new HttpEntity<>(createVectumHeaders()),
                ResponseModel.class
        ).getBody();
        requireSucceeded(response, "查询数据推送任务失败");
        JsonNode data = JacksonConfig.OBJECT_MAPPER.valueToTree(response.getData());
        List<PushTaskSummaryVo> items = new ArrayList<>();
        for (JsonNode item : data.path("items")) {
            items.add(JacksonConfig.OBJECT_MAPPER.convertValue(item, PushTaskSummaryVo.class));
        }
        return PushTaskPageVo.builder()
                .items(items)
                .total(data.path("total").asLong(items.size()))
                .offset(data.path("offset").asInt(0))
                .limit(data.path("limit").asInt(items.size()))
                .hasMore(data.path("hasMore").asBoolean(false))
                .build();
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

    private PushTaskOperationVo operationRequest(String action,
                                                 Integer id,
                                                 String endpoint,
                                                 String previousStatus) {
        ResponseModel response = restTemplate.exchange(
                customWebConfig.getDataServiceUrl() + "/vectum/api/v1/task/{id}/" + endpoint,
                HttpMethod.POST,
                new HttpEntity<>(createVectumHeaders()),
                ResponseModel.class,
                id
        ).getBody();
        requireSucceeded(response, action + " 数据推送任务失败");
        PushTaskVo task = findById(id);
        return operation(action, previousStatus, task, true);
    }

    private PushTaskOperationVo operation(String action,
                                          String previousStatus,
                                          PushTaskVo task,
                                          boolean changed) {
        return PushTaskOperationVo.builder()
                .action(action)
                .taskId(task.getId())
                .previousStatus(previousStatus)
                .status(task.getStatus())
                .changed(changed)
                .pid(task.getPid())
                .currentRunId(task.getCurrentRunId())
                .runStartedAt(task.getRunStartedAt())
                .build();
    }

    private void requireSucceeded(ResponseModel response, String fallbackMessage) {
        if (response != null && response.succeed()) {
            return;
        }
        String message = response == null || !StringUtils.hasText(response.getMsg())
                ? fallbackMessage
                : response.getMsg();
        throw new ApiException(ResultCodeEnum.UNKNOWN_ERROR.getCode(), message);
    }

    private void queryParam(UriComponentsBuilder builder, String name, String value) {
        if (StringUtils.hasText(value)) {
            builder.queryParam(name, value.trim());
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

    private void requireValidation(PushTaskDto pushTaskDto) {
        if (pushTaskDto == null) {
            throw new PushTaskConfigException("推送任务参数不能为空");
        }
        PushTaskValidationRequest request = new PushTaskValidationRequest();
        request.setConfig(pushTaskDto.getConfig());
        request.setFormat(pushTaskDto.getFormat());
        request.setMetaFileName(pushTaskDto.getMetaFileName());
        request.setMetaContent(pushTaskDto.getMetaContent());
        PushTaskValidationVo validation = validationService.validate(request);
        if (validation == null || validation.isBlocked()) {
            throw new PushTaskConfigException("配置校验服务不可用，已禁止创建或修复推送任务");
        }
        if (!validation.isPassed()) {
            throw new PushTaskConfigException("配置未通过平台门禁：" + validationFailureSummary(validation));
        }
        if (!StringUtils.hasText(pushTaskDto.getCandidateDigest())
                || !StringUtils.hasText(validation.getCandidateDigest())) {
            throw new PushTaskConfigException("缺少候选配置摘要，必须重新执行配置校验");
        }
        if (!Objects.equals(pushTaskDto.getCandidateDigest(),
                validation.getCandidateDigest())) {
            throw new PushTaskConfigException("候选配置摘要与最新校验结果不一致");
        }
        if (!StringUtils.hasText(pushTaskDto.getMetaDigest())
                || !Objects.equals(pushTaskDto.getMetaDigest(), validation.getMetaDigest())) {
            throw new PushTaskConfigException("Meta 摘要与最新校验结果不一致");
        }
        if (!StringUtils.hasText(pushTaskDto.getValidationDigest())
                || !StringUtils.hasText(validation.getValidationDigest())
                || !Objects.equals(pushTaskDto.getValidationDigest(),
                validation.getValidationDigest())) {
            throw new PushTaskConfigException("校验结果已变化，必须重新确认配置");
        }
    }

    private void enforceProxyValidation(HttpServletRequest request, byte[] body) {
        String method = request.getMethod() == null
                ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI()
                .replace("/api/v1/system/push-task", "");
        boolean create = "POST".equals(method) && "/add".equals(path);
        boolean update = "PUT".equals(method)
                && ("/batch".equals(path) || path.matches("/\\d+"));
        if (!create && !update) {
            return;
        }
        PushTaskDto pushTaskDto;
        try {
            pushTaskDto = JacksonConfig.OBJECT_MAPPER.readValue(body, PushTaskDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("创建或更新推送任务时请求体必须是合法 JSON", e);
        }
        requireValidation(pushTaskDto);
    }

    private String validationFailureSummary(PushTaskValidationVo validation) {
        List<PushTaskDiagnosticVo> diagnostics = validation.getDiagnostics();
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "未返回诊断详情";
        }
        return diagnostics.stream()
                .filter(item -> "ERROR".equalsIgnoreCase(item.getSeverity()))
                .limit(5)
                .map(item -> item.getCode() + " " + item.getPath() + " " + item.getMessage()
                        + "；修复建议：" + item.getRepairHint())
                .collect(Collectors.joining(" | "));
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
