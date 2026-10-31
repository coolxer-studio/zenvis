package com.coolxer.controller.system;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.dto.PushTaskQueryDto;
import com.coolxer.model.system.dto.PushTaskValidationRequest;
import com.coolxer.model.system.validation.PushTaskDiagnosticResultVo;
import com.coolxer.model.system.validation.PushTaskValidationVo;
import com.coolxer.model.system.vo.PushTaskOperationVo;
import com.coolxer.model.system.vo.PushTaskPageVo;
import com.coolxer.model.system.vo.PushTaskSummaryVo;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.dih.mcp.McpPreExecutionException;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.system.PushTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushTaskMcpToolTest {

    private final FakePushTaskService service = new FakePushTaskService();
    private final ConfigService configService = mock(ConfigService.class);
    private final PushTaskMcpTool tool = new PushTaskMcpTool(service, configService);

    @Test
    void exposesExactlyElevenLifecycleToolsAndRemovesLegacyTools() {
        List<ToolCallback> callbacks = List.of(MethodToolCallbackProvider.builder()
                .toolObjects(tool)
                .build()
                .getToolCallbacks());

        assertThat(callbacks)
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder(
                        "push_task_list",
                        "push_task_get",
                        "push_task_get_log",
                        "push_task_diagnose",
                        "push_task_validate_config",
                        "push_task_create_and_start",
                        "push_task_update_and_restart",
                        "push_task_start",
                        "push_task_stop",
                        "push_task_restart",
                        "push_task_delete")
                .doesNotContain(
                        "push_task_detect_format",
                        "push_task_list_by_source_mark",
                        "push_task_delete_by_source_mark",
                        "push_task_repair_and_restart");
    }

    @Test
    void createNormalizesExplicitYmlFormatAndReturnsStructuredOperation() {
        PushTaskDto request = request("sources: {}", "yml");
        service.createOperation = operation("create_and_start", 12, "running");

        PushTaskOperationVo result = tool.createAndStart(request);

        assertThat(result.getTaskId()).isEqualTo(12);
        assertThat(service.createRequest).isSameAs(request);
        assertThat(request.getFormat()).isEqualTo("yaml");
    }

    @Test
    void createRequiresSourceSystemMarkAndExplicitFormat() {
        PushTaskDto missingFormat = request("sources: {}", null);
        assertThatThrownBy(() -> tool.createAndStart(missingFormat))
                .isInstanceOf(McpPreExecutionException.class)
                .hasMessageContaining("format");

        PushTaskDto wrongSource = request("sources: {}", "yaml");
        wrongSource.setSource("WEB");
        assertThatThrownBy(() -> tool.createAndStart(wrongSource))
                .isInstanceOf(McpPreExecutionException.class)
                .hasMessageContaining("SYSTEM");
    }

    @Test
    void createReusesSameSystemMarkWithoutCallingCreateAgain() {
        PushTaskDto request = request("sources: {}", "yaml");
        PushTaskSummaryVo summary = new PushTaskSummaryVo();
        summary.setId(12);
        service.queryPage = PushTaskPageVo.builder()
                .items(List.of(summary))
                .total(1)
                .offset(0)
                .limit(100)
                .hasMore(false)
                .build();
        PushTaskVo existing = task(12, "SYSTEM", "source-mark", "sources: {}");
        existing.setStatus("running");
        service.findByIdResult = existing;

        PushTaskOperationVo result = tool.createAndStart(request);

        assertThat(service.createRequest).isNull();
        assertThat(result.getTaskId()).isEqualTo(12);
        assertThat(result.isChanged()).isFalse();
    }

    @Test
    void createRejectsExistingMarkWithDifferentConfig() {
        PushTaskDto request = request("sources: {new: true}", "yaml");
        PushTaskSummaryVo summary = new PushTaskSummaryVo();
        summary.setId(12);
        service.queryPage = PushTaskPageVo.builder()
                .items(List.of(summary))
                .total(1)
                .offset(0)
                .limit(100)
                .hasMore(false)
                .build();
        service.findByIdResult = task(12, "SYSTEM", "source-mark", "sources: {old: true}");

        assertThatThrownBy(() -> tool.createAndStart(request))
                .isInstanceOf(McpPreExecutionException.class)
                .hasMessageContaining("更新流程");
        assertThat(service.createRequest).isNull();
    }

    @Test
    void createRejectsExistingMarkWithDifferentExplicitFormat() {
        PushTaskDto request = request("sources: {}", "toml");
        PushTaskSummaryVo summary = new PushTaskSummaryVo();
        summary.setId(12);
        service.queryPage = PushTaskPageVo.builder()
                .items(List.of(summary))
                .total(1)
                .offset(0)
                .limit(100)
                .hasMore(false)
                .build();
        service.findByIdResult = task(12, "SYSTEM", "source-mark", "sources: {}");

        assertThatThrownBy(() -> tool.createAndStart(request))
                .isInstanceOf(McpPreExecutionException.class)
                .hasMessageContaining("格式");
        assertThat(service.createRequest).isNull();
    }

    @Test
    void listMapsAllFiltersAndCapsPageSize() {
        service.queryPage = PushTaskPageVo.builder().items(List.of()).build();

        tool.list("mark-1", "事件", "MCP", "running", -5, 500);

        assertThat(service.query.getSourceMark()).isEqualTo("mark-1");
        assertThat(service.query.getNameContains()).isEqualTo("事件");
        assertThat(service.query.getSource()).isEqualTo("MCP");
        assertThat(service.query.getStatus()).isEqualTo("running");
        assertThat(service.query.getOffset()).isZero();
        assertThat(service.query.getLimit()).isEqualTo(100);
    }

    @Test
    void getReturnsCompleteConfigurationFromDetailEndpoint() {
        service.findByIdResult = task(12, "WEB", "web-mark", "sources: {}");

        PushTaskVo result = tool.get(12);

        assertThat(result.getConfig()).isEqualTo("sources: {}");
        assertThat(result.getSource()).isEqualTo("WEB");
    }

    @Test
    void updatePreservesSourceAndMarkAndReturnsRestartRunMetadata() {
        service.findByIdResult = task(12, "API", "api-mark", "sources: {}");
        service.updateOperation = operation("update_and_restart", 12, "running");
        PushTaskDto request = request("sinks: {}", "yaml");
        request.setSource("API");
        request.setMark("api-mark");

        PushTaskOperationVo result = tool.updateAndRestart(12, request);

        assertThat(result.getAction()).isEqualTo("update_and_restart");
        assertThat(service.updateTaskId).isEqualTo(12);
        assertThat(service.updateRequest.getSource()).isNull();
        assertThat(service.updateRequest.getMark()).isNull();
    }

    @Test
    void updateRejectsOwnershipChanges() {
        service.findByIdResult = task(12, "API", "api-mark", "sources: {}");
        PushTaskDto request = request("sinks: {}", "yaml");
        request.setSource("API");
        request.setMark("other-mark");

        assertThatThrownBy(() -> tool.updateAndRestart(12, request))
                .isInstanceOf(McpPreExecutionException.class)
                .hasMessageContaining("mark");
    }

    @Test
    void lifecycleToolsDelegateByIdAndReturnOperation() {
        service.findByIdResult = task(12, "SYSTEM", "mark", "sources: {}");
        service.startOperation = operation("start", 12, "running");
        service.stopOperation = operation("stop", 12, "stopped");
        service.restartOperation = operation("restart", 12, "running");
        service.deleteOperation = operation("delete", 12, "deleted");

        assertThat(tool.start(12).getAction()).isEqualTo("start");
        assertThat(tool.stop(12).getAction()).isEqualTo("stop");
        assertThat(tool.restart(12).getAction()).isEqualTo("restart");
        assertThat(tool.delete(12).getAction()).isEqualTo("delete");
        assertThat(service.startTaskId).isEqualTo(12);
        assertThat(service.stopTaskId).isEqualTo(12);
        assertThat(service.restartTaskId).isEqualTo(12);
        assertThat(service.deleteTaskId).isEqualTo(12);
    }

    @Test
    void validateConfigPassesExplicitFormatAndMeta() {
        when(configService.fileExistsInConfigPath("meta", "user_event.json"))
                .thenReturn(true);
        when(configService.readFile("meta", "user_event.json"))
                .thenReturn("{\"entity\":[]}");
        service.validationResult = PushTaskValidationVo.builder()
                .passed(true)
                .format("toml")
                .candidateDigest("candidate")
                .build();

        PushTaskValidationVo result = tool.validateConfig(
                "[sources.demo]\ntype = \"demo_logs\"",
                "toml", "user_event.json");

        assertThat(result.isPassed()).isTrue();
        assertThat(service.validationRequest.getFormat()).isEqualTo("toml");
        assertThat(service.validationRequest.getMetaFileName()).isEqualTo("user_event.json");
        assertThat(service.validationRequest.getMetaContent()).isEqualTo("{\"entity\":[]}");
    }

    @Test
    void diagnoseUsesOnlyTaskIdAndLogIsBoundedAndRedacted() {
        service.findByIdResult = task(12, "MCP", "mark", "sources: {}");
        service.diagnosticResult = PushTaskDiagnosticResultVo.builder()
                .verdict("RUNNING")
                .runId("run-1")
                .build();
        service.log = "first\npassword=secret\nhttps://user:pass@example.test\nlast";

        assertThat(tool.diagnose(12).getRunId()).isEqualTo("run-1");
        Map<String, Object> result = tool.getLog(12, "system", 3);

        assertThat(service.diagnoseTaskId).isEqualTo(12);
        assertThat(result).containsEntry("logType", "system");
        assertThat(result.get("content").toString())
                .contains("password=***", "https://***@example.test", "last")
                .doesNotContain("secret", "user:pass", "first");
    }

    private PushTaskDto request(String config, String format) {
        PushTaskDto request = new PushTaskDto();
        request.setName("推送任务");
        request.setDescription("description");
        request.setSource("SYSTEM");
        request.setMark("source-mark");
        request.setConfig(config);
        request.setFormat(format);
        return request;
    }

    private PushTaskVo task(int id, String source, String mark, String config) {
        PushTaskVo task = new PushTaskVo();
        task.setId(id);
        task.setName("推送任务");
        task.setSource(source);
        task.setMark(mark);
        task.setConfig(config);
        task.setFormat("yaml");
        task.setStatus("running");
        task.setCurrentRunId("run-1");
        return task;
    }

    private PushTaskOperationVo operation(String action, int taskId, String status) {
        return PushTaskOperationVo.builder()
                .action(action)
                .taskId(taskId)
                .previousStatus("stopped")
                .status(status)
                .changed(true)
                .currentRunId("run-1")
                .build();
    }

    private static class FakePushTaskService implements PushTaskService {
        private PushTaskQueryDto query;
        private PushTaskPageVo queryPage = PushTaskPageVo.builder().items(List.of()).build();
        private PushTaskDto createRequest;
        private PushTaskOperationVo createOperation;
        private PushTaskVo findByIdResult;
        private Integer updateTaskId;
        private PushTaskDto updateRequest;
        private PushTaskOperationVo updateOperation;
        private Integer startTaskId;
        private Integer stopTaskId;
        private Integer restartTaskId;
        private Integer deleteTaskId;
        private PushTaskOperationVo startOperation;
        private PushTaskOperationVo stopOperation;
        private PushTaskOperationVo restartOperation;
        private PushTaskOperationVo deleteOperation;
        private PushTaskValidationRequest validationRequest;
        private PushTaskValidationVo validationResult;
        private Integer diagnoseTaskId;
        private PushTaskDiagnosticResultVo diagnosticResult;
        private String log;

        @Override
        public Object proxy(HttpServletRequest request) {
            return null;
        }

        @Override
        public boolean createAndStart(PushTaskDto pushTaskDto) {
            return createAndStartTask(pushTaskDto) != null;
        }

        @Override
        public PushTaskOperationVo createAndStartTask(PushTaskDto pushTaskDto) {
            createRequest = pushTaskDto;
            return createOperation;
        }

        @Override
        public boolean updateAndStart(Integer id, PushTaskDto pushTaskDto) {
            updateTaskId = id;
            updateRequest = pushTaskDto;
            return updateOperation != null;
        }

        @Override
        public PushTaskOperationVo updateAndRestartTask(Integer id, PushTaskDto pushTaskDto) {
            updateTaskId = id;
            updateRequest = pushTaskDto;
            return updateOperation;
        }

        @Override
        public PushTaskOperationVo deleteTask(Integer id) {
            deleteTaskId = id;
            return deleteOperation;
        }

        @Override
        public List<PushTaskVo> findAll() {
            return List.of();
        }

        @Override
        public PushTaskPageVo query(PushTaskQueryDto query) {
            this.query = query;
            return queryPage;
        }

        @Override
        public PushTaskVo findById(Integer id) {
            return findByIdResult;
        }

        @Override
        public List<PushTaskVo> findBySourceMark(String sourceMark) {
            return List.of();
        }

        @Override
        public boolean deleteBySourceMark(String sourceMark) {
            return false;
        }

        @Override
        public String getLog(Integer id, String logType) {
            return log;
        }

        @Override
        public PushTaskOperationVo startTask(Integer id) {
            startTaskId = id;
            return startOperation;
        }

        @Override
        public PushTaskOperationVo stopTask(Integer id) {
            stopTaskId = id;
            return stopOperation;
        }

        @Override
        public PushTaskOperationVo restartTask(Integer id) {
            restartTaskId = id;
            return restartOperation;
        }

        @Override
        public PushTaskValidationVo validateConfig(PushTaskValidationRequest request) {
            validationRequest = request;
            return validationResult;
        }

        @Override
        public PushTaskDiagnosticResultVo diagnose(Integer id) {
            diagnoseTaskId = id;
            return diagnosticResult;
        }
    }
}
