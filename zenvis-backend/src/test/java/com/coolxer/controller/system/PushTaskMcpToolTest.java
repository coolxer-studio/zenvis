package com.coolxer.controller.system;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.system.PushTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushTaskMcpToolTest {

    private final FakePushTaskService pushTaskService = new FakePushTaskService();
    private final PushTaskMcpTool tool = new PushTaskMcpTool(pushTaskService);

    @Test
    void createAndStartDelegatesToService() {
        PushTaskDto request = new PushTaskDto();
        request.setName("持续分析数据匹配");
        request.setSource("SYSTEM");
        request.setMark("source-mark");
        request.setConfig("sources: {}");
        pushTaskService.createAndStartResult = true;

        assertThat(tool.createAndStart(request)).isTrue();

        assertThat(pushTaskService.createAndStartRequest).isSameAs(request);
    }

    @Test
    void listBySourceMarkDelegatesToService() {
        PushTaskVo task = new PushTaskVo();
        task.setName("持续分析数据匹配");
        pushTaskService.findBySourceMarkResult = List.of(task);

        assertThat(tool.listBySourceMark("analysis-risk")).containsExactly(task);

        assertThat(pushTaskService.findBySourceMarkValue).isEqualTo("analysis-risk");
    }

    @Test
    void deleteBySourceMarkDelegatesToService() {
        pushTaskService.deleteBySourceMarkResult = true;

        assertThat(tool.deleteBySourceMark("analysis-risk")).isTrue();

        assertThat(pushTaskService.deleteBySourceMarkValue).isEqualTo("analysis-risk");
    }

    @Test
    void detectFormatDelegatesToService() {
        pushTaskService.detectFormatResult = "yaml";

        assertThat(tool.detectFormat("sources: {}")).isEqualTo("yaml");

        assertThat(pushTaskService.detectFormatContent).isEqualTo("sources: {}");
    }

    @Test
    void getLogReturnsLatestSanitizedBoundedLogForOwnedTask() {
        PushTaskVo task = managedTask(12, "data-access:chat:service-log");
        task.setStatus("running[error]");
        pushTaskService.findByIdResult = task;
        pushTaskService.getLogResult = IntStream.rangeClosed(1, 120)
                .mapToObj(index -> "line-" + index)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("")
                + "\npassword=\"top-secret\" Authorization: Bearer secret-token";

        Map<String, Object> result = tool.getLog(
                12,
                "data-access:chat:service-log",
                "system"
        );

        assertThat(result)
                .containsEntry("taskId", 12)
                .containsEntry("sourceMark", "data-access:chat:service-log")
                .containsEntry("logType", "system")
                .containsEntry("taskStatus", "running[error]")
                .containsEntry("truncated", true);
        assertThat(result.get("content").toString())
                .doesNotContain("line-1\n", "top-secret", "secret-token", "Bearer")
                .contains("line-22", "line-120", "password=\"***", "Authorization: ***");
        assertThat(pushTaskService.getLogTaskId).isEqualTo(12);
        assertThat(pushTaskService.getLogType).isEqualTo("system");
        assertThat(pushTaskService.findByIdValue).isEqualTo(12);
        assertThat(pushTaskService.findBySourceMarkValue).isNull();
    }

    @Test
    void getLogCapsLongContentFromTheTail() {
        PushTaskVo task = managedTask(12, "data-access:chat:service-log");
        pushTaskService.findByIdResult = task;
        pushTaskService.getLogResult = IntStream.rangeClosed(1, 100)
                .mapToObj(index -> index + "-" + "x".repeat(100))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        Map<String, Object> result = tool.getLog(
                12,
                "data-access:chat:service-log",
                "console"
        );

        assertThat(result.get("content").toString()).hasSize(6_000).endsWith("x".repeat(100));
        assertThat(result).containsEntry("truncated", true);
    }

    @Test
    void getLogRejectsMismatchedTaskOwnership() {
        pushTaskService.findByIdResult = managedTask(12, "data-access:chat:other");

        assertThatThrownBy(() -> tool.getLog(
                12,
                "data-access:chat:service-log",
                "system"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不匹配");
    }

    @Test
    void repairAndRestartDelegatesCompleteManagedTask() {
        String sourceMark = "data-access:chat:service-log";
        pushTaskService.findByIdResult = managedTask(12, sourceMark);
        pushTaskService.updateAndStartResult = true;
        PushTaskDto request = new PushTaskDto();
        request.setName("service-log");
        request.setConfig("sources: {}");
        request.setSource("SYSTEM");
        request.setMark(sourceMark);

        assertThat(tool.repairAndRestart(12, sourceMark, request)).isTrue();

        assertThat(pushTaskService.updateAndStartTaskId).isEqualTo(12);
        assertThat(pushTaskService.updateAndStartRequest).isSameAs(request);
        assertThat(pushTaskService.findBySourceMarkValue).isNull();
    }

    @Test
    void repairAndRestartRejectsMismatchedHistoricalTaskId() {
        String sourceMark = "data-access:chat:service-log";
        pushTaskService.findByIdResult = managedTask(
                13, "data-access:chat:other");
        PushTaskDto request = new PushTaskDto();
        request.setName("service-log");
        request.setConfig("sources: {}");
        request.setSource("SYSTEM");
        request.setMark(sourceMark);

        assertThatThrownBy(() -> tool.repairAndRestart(12, sourceMark, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不匹配");
        assertThat(pushTaskService.updateAndStartRequest).isNull();
    }

    @Test
    void exposesOnlyLogDrivenPushTaskToolsWithoutValidationId() throws Exception {
        List<ToolCallback> callbacks = List.of(MethodToolCallbackProvider.builder()
                .toolObjects(tool)
                .build()
                .getToolCallbacks());

        assertThat(callbacks)
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder(
                        "push_task_create_and_start",
                        "push_task_list_by_source_mark",
                        "push_task_delete_by_source_mark",
                        "push_task_get_log",
                        "push_task_repair_and_restart",
                        "push_task_detect_format")
                .doesNotContain(
                        "push_task_vector_capabilities",
                        "push_task_vector_component_schema",
                        "push_task_generate_config",
                        "push_task_validate_config");

        for (String toolName : List.of(
                "push_task_create_and_start",
                "push_task_repair_and_restart")) {
            String schemaText = callbacks.stream()
                    .filter(callback -> toolName.equals(
                            callback.getToolDefinition().name()))
                    .findFirst()
                    .orElseThrow()
                    .getToolDefinition()
                    .inputSchema();
            JsonNode schema = JacksonConfig.OBJECT_MAPPER.readTree(schemaText);
            assertThat(schema.path("properties").has("validationId")).isFalse();
        }
    }

    private PushTaskVo managedTask(int id, String sourceMark) {
        PushTaskVo task = new PushTaskVo();
        task.setId(id);
        task.setSource("SYSTEM");
        task.setMark(sourceMark);
        return task;
    }

    private static class FakePushTaskService implements PushTaskService {
        private boolean createAndStartResult;
        private PushTaskDto createAndStartRequest;
        private boolean updateAndStartResult;
        private Integer updateAndStartTaskId;
        private PushTaskDto updateAndStartRequest;
        private PushTaskVo findByIdResult;
        private Integer findByIdValue;
        private List<PushTaskVo> findBySourceMarkResult = List.of();
        private String findBySourceMarkValue;
        private boolean deleteBySourceMarkResult;
        private String deleteBySourceMarkValue;
        private Integer getLogTaskId;
        private String getLogType;
        private String getLogResult;
        private String detectFormatResult;
        private String detectFormatContent;

        @Override
        public Object proxy(HttpServletRequest request) {
            return null;
        }

        @Override
        public boolean createAndStart(PushTaskDto pushTaskDto) {
            createAndStartRequest = pushTaskDto;
            return createAndStartResult;
        }

        @Override
        public boolean updateAndStart(Integer id, PushTaskDto pushTaskDto) {
            updateAndStartTaskId = id;
            updateAndStartRequest = pushTaskDto;
            return updateAndStartResult;
        }

        @Override
        public List<PushTaskVo> findAll() {
            return List.of();
        }

        @Override
        public PushTaskVo findById(Integer id) {
            findByIdValue = id;
            return findByIdResult;
        }

        @Override
        public List<PushTaskVo> findBySourceMark(String sourceMark) {
            findBySourceMarkValue = sourceMark;
            return findBySourceMarkResult;
        }

        @Override
        public boolean deleteBySourceMark(String sourceMark) {
            deleteBySourceMarkValue = sourceMark;
            return deleteBySourceMarkResult;
        }

        @Override
        public String getLog(Integer id, String logType) {
            getLogTaskId = id;
            getLogType = logType;
            return getLogResult;
        }

        @Override
        public String detectFormat(String content) {
            detectFormatContent = content;
            return detectFormatResult;
        }

    }
}
