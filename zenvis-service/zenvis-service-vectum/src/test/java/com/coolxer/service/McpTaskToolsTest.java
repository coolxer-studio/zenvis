package com.coolxer.service;

import com.coolxer.model.diagnostic.RuntimeDiagnostic;
import com.coolxer.model.dto.TaskDto;
import com.coolxer.model.dto.TaskQueryDto;
import com.coolxer.model.validation.ConfigValidationResult;
import com.coolxer.model.validation.ValidationRequest;
import com.coolxer.model.vo.TaskOperationVo;
import com.coolxer.model.vo.TaskPageVo;
import com.coolxer.model.vo.TaskVo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpTaskToolsTest {

    private final TaskService taskService = mock(TaskService.class);
    private final TaskValidationService validationService = mock(TaskValidationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpTaskTools tools = new McpTaskTools(
            taskService, validationService, objectMapper);

    @BeforeEach
    void setUp() {
        when(taskService.start(any())).thenReturn(operation("start", "running"));
        when(taskService.stop(any())).thenReturn(operation("stop", "stopped"));
        when(taskService.restart(any())).thenReturn(operation("restart", "running"));
    }

    @Test
    void exposesOnlyLifecycleMcpTools() {
        List<String> names = List.of(MethodToolCallbackProvider.builder()
                        .toolObjects(tools)
                        .build()
                        .getToolCallbacks())
                .stream()
                .map(ToolCallback::getToolDefinition)
                .map(definition -> definition.name())
                .toList();

        assertThat(names).containsExactlyInAnyOrder(
                "listTasks", "getTask", "createTask", "updateTask", "deleteTask",
                "startTask", "stopTask", "restartTask", "getTaskLog",
                "diagnoseTask", "validateConfig")
                .doesNotContain("getTasks", "toggleTask", "batchDeleteTasks");
    }

    @Test
    void listTasksMapsAllFiltersAndReturnsSummaryPage() throws Exception {
        when(taskService.query(any(TaskQueryDto.class))).thenReturn(
                TaskPageVo.builder().total(0).offset(2).limit(5).build());

        String result = tools.listTasks("SYSTEM", "mark-1", "Event", "running", 2, 5);

        ArgumentCaptor<TaskQueryDto> captor = ArgumentCaptor.forClass(TaskQueryDto.class);
        verify(taskService).query(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("SYSTEM");
        assertThat(captor.getValue().getMark()).isEqualTo("mark-1");
        assertThat(captor.getValue().getNameContains()).isEqualTo("Event");
        assertThat(captor.getValue().getStatus()).isEqualTo("running");
        assertThat(captor.getValue().getOffset()).isEqualTo(2);
        assertThat(captor.getValue().getLimit()).isEqualTo(5);
        assertThat(objectMapper.readTree(result).path("total").asInt()).isZero();
    }

    @Test
    void createTaskRequiresExplicitFormatAndMarksMcpSource() {
        TaskVo created = task(12L, "created");
        when(taskService.create(any(TaskDto.class))).thenReturn(created);

        assertThatThrownBy(() -> tools.createTask("task", "desc", "sources: {}", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式");
        verify(taskService, never()).create(any(TaskDto.class));

        tools.createTask("task", "desc", "sources: {}", "yml");

        ArgumentCaptor<TaskDto> captor = ArgumentCaptor.forClass(TaskDto.class);
        verify(taskService).create(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("MCP");
        assertThat(captor.getValue().getFormat()).isEqualTo("yml");
    }

    @Test
    void updateTaskKeepsOwnershipFieldsUnsetAndUsesExplicitFormat() {
        when(taskService.info(12L)).thenReturn(task(12L, "current"));
        when(taskService.update(any(), any())).thenReturn(true);

        tools.updateTask("12", "updated", "desc", "sinks: {}", "toml");

        ArgumentCaptor<TaskDto> captor = ArgumentCaptor.forClass(TaskDto.class);
        verify(taskService).update(org.mockito.ArgumentMatchers.eq(12L), captor.capture());
        assertThat(captor.getValue().getFormat()).isEqualTo("toml");
        assertThat(captor.getValue().getSource()).isNull();
        assertThat(captor.getValue().getMark()).isNull();
    }

    @Test
    void deleteTaskUsesExactIdAndLifecycleReturnsStructuredActions() throws Exception {
        when(taskService.info(12L)).thenReturn(task(12L, "current"));

        tools.deleteTask("12");
        verify(taskService).delete(12L);

        JsonNode start = objectMapper.readTree(tools.startTask("12"));
        JsonNode stop = objectMapper.readTree(tools.stopTask("12"));
        JsonNode restart = objectMapper.readTree(tools.restartTask("12"));
        assertThat(start.path("action").asText()).isEqualTo("start");
        assertThat(stop.path("action").asText()).isEqualTo("stop");
        assertThat(restart.path("action").asText()).isEqualTo("restart");
        verify(taskService).start(12L);
        verify(taskService).stop(12L);
        verify(taskService).restart(12L);
    }

    @Test
    void detailLogAndDiagnosePreserveCurrentRunMetadata() throws Exception {
        TaskVo task = task(12L, "current");
        when(taskService.info(12L)).thenReturn(task);
        when(taskService.log(12L, "console")).thenReturn("started");
        when(taskService.diagnostics(12L)).thenReturn(RuntimeDiagnostic.builder()
                .verdict("RUNNING").runId("run-1").taskStatus("running").build());

        JsonNode detail = objectMapper.readTree(tools.getTask("12"));
        JsonNode log = objectMapper.readTree(tools.getTaskLog("12", "console"));
        JsonNode diagnosis = objectMapper.readTree(tools.diagnoseTask("12"));

        assertThat(detail.path("config").asText()).isEqualTo("sources: {}");
        assertThat(log.path("currentRunId").asText()).isEqualTo("run-1");
        assertThat(log.path("content").asText()).isEqualTo("started");
        assertThat(diagnosis.path("runId").asText()).isEqualTo("run-1");
    }

    @Test
    void validateConfigRequiresFormatAndDelegatesNativeValidation() throws Exception {
        when(validationService.validate(any(ValidationRequest.class))).thenReturn(
                ConfigValidationResult.builder().passed(true).format("yaml").build());

        assertThatThrownBy(() -> tools.validateConfig("sources: {}", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式");
        verify(validationService, never()).validate(any(ValidationRequest.class));

        JsonNode result = objectMapper.readTree(
                tools.validateConfig("sources: {}", "yaml", true));

        ArgumentCaptor<ValidationRequest> captor = ArgumentCaptor.forClass(ValidationRequest.class);
        verify(validationService).validate(captor.capture());
        assertThat(captor.getValue().getFormat()).isEqualTo("yaml");
        assertThat(captor.getValue().isTestsRequired()).isTrue();
        assertThat(result.path("passed").asBoolean()).isTrue();
    }

    private TaskVo task(long id, String name) {
        TaskVo task = new TaskVo();
        task.setId(id);
        task.setName(name);
        task.setSource("MCP");
        task.setMark("mark-1");
        task.setConfig("sources: {}");
        task.setFormat("yaml");
        task.setStatus("running");
        task.setCurrentRunId("run-1");
        return task;
    }

    private TaskOperationVo operation(String action, String status) {
        return TaskOperationVo.builder()
                .action(action)
                .taskId(12L)
                .previousStatus("stopped")
                .status(status)
                .changed(true)
                .currentRunId("run-1")
                .build();
    }
}
