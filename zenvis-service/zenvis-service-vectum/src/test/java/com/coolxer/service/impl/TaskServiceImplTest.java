package com.coolxer.service.impl;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.coolxer.commons.enums.TaskSourceEnum;
import com.coolxer.dao.TaskRepository;
import com.coolxer.model.RunMetadata;
import com.coolxer.model.Task;
import com.coolxer.model.dto.TaskDto;
import com.coolxer.model.dto.TaskQueryDto;
import com.coolxer.model.vo.TaskOperationVo;
import com.coolxer.model.vo.TaskPageVo;
import com.coolxer.model.vo.TaskVo;
import com.coolxer.service.VectorService;
import com.coolxer.service.ZenvisBusinessService;
import com.coolxer.service.TaskDiagnosticsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务服务 Zenvis 事件联动测试。
 */
class TaskServiceImplTest {

    private VectorService vectorService;
    private TaskRepository taskRepository;
    private ZenvisBusinessService zenvisBusinessService;
    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        vectorService = mock(VectorService.class);
        taskRepository = mock(TaskRepository.class);
        zenvisBusinessService = mock(ZenvisBusinessService.class);
        taskService = new TaskServiceImpl(vectorService, taskRepository,
                zenvisBusinessService, mock(TaskDiagnosticsService.class));
    }

    @Test
    void shouldReportCreatedEventAfterTaskAndProcessAreCreated() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(10L);
            return task;
        });
        when(vectorService.createProcess(any(), any(), any(), any())).thenReturn(null);

        taskService.create(taskDto());

        verify(zenvisBusinessService).reportTaskCreated(any(Task.class));
    }

    @Test
    void shouldReportUpdatedEventAfterProcessConfigurationIsUpdated() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(vectorService.updateProcess(any(), any(), any(), any())).thenReturn(null);
        when(taskRepository.save(task)).thenReturn(task);

        boolean updated = taskService.update(10L, taskDto());

        assertThat(updated).isTrue();
        verify(zenvisBusinessService).reportTaskUpdated(task);
    }

    @Test
    void shouldReportDeletedEventOnlyAfterProcessIsDeleted() {
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task()));
        when(vectorService.deleteProcess("10")).thenReturn(true);

        taskService.delete(10L);

        verify(taskRepository).deleteById(10L);
        verify(zenvisBusinessService).reportTaskDeleted(10L);
    }

    @Test
    void shouldNotReportDeletedEventWhenProcessDeletionFails() {
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task()));
        when(vectorService.deleteProcess("10")).thenReturn(false);

        assertThatThrownBy(() -> taskService.delete(10L))
                .isInstanceOf(com.coolxer.commons.exception.ApiException.class)
                .hasMessageContaining("删除任务进程失败");

        verify(taskRepository, never()).deleteById(10L);
        verify(zenvisBusinessService, never()).reportTaskDeleted(10L);
    }

    @Test
    void shouldReportStartedEventAfterTaskStarts() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(vectorService.status("10")).thenReturn("stopped");
        when(vectorService.startProcess("10")).thenReturn(4321L);

        boolean toggled = taskService.toggle(10L);

        assertThat(toggled).isTrue();
        assertThat(task.getPid()).isEqualTo(4321);
        verify(zenvisBusinessService).reportTaskStarted(task);
    }

    @Test
    void shouldReportStoppedEventAfterTaskStops() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(vectorService.status("10")).thenReturn("running");
        when(vectorService.stopProcess("10")).thenReturn(true);

        boolean toggled = taskService.toggle(10L);

        assertThat(toggled).isTrue();
        assertThat(task.getPid()).isZero();
        verify(zenvisBusinessService).reportTaskStopped(task);
    }

    @Test
    void queryReturnsNewestFirstAndPaginatesAcrossAllSources() {
        Task oldest = task(1L, "oldest", TaskSourceEnum.WEB, "2026-10-30 09:00:00");
        Task middle = task(2L, "middle", TaskSourceEnum.MCP, "2026-10-30 10:00:00");
        Task newest = task(3L, "newest", TaskSourceEnum.SYSTEM, "2026-10-30 11:00:00");
        when(taskRepository.findAll()).thenReturn(List.of(oldest, middle, newest));
        when(vectorService.status(anyString())).thenReturn("stopped");

        TaskQueryDto firstPageQuery = new TaskQueryDto();
        firstPageQuery.setLimit(2);
        TaskPageVo firstPage = taskService.query(firstPageQuery);

        assertThat(firstPage.getItems()).extracting("id").containsExactly(3L, 2L);
        assertThat(firstPage.getTotal()).isEqualTo(3);
        assertThat(firstPage.isHasMore()).isTrue();

        TaskQueryDto secondPageQuery = new TaskQueryDto();
        secondPageQuery.setOffset(2);
        secondPageQuery.setLimit(2);
        TaskPageVo secondPage = taskService.query(secondPageQuery);

        assertThat(secondPage.getItems()).extracting("id").containsExactly(1L);
        assertThat(secondPage.isHasMore()).isFalse();
    }

    @Test
    void queryAppliesSourceMarkNameAndStatusFilters() {
        Task running = task(1L, "Student Event", TaskSourceEnum.SYSTEM, "2026-10-30 11:00:00");
        running.setMark("mark-1");
        Task otherMark = task(2L, "Student Event", TaskSourceEnum.SYSTEM, "2026-10-30 10:00:00");
        otherMark.setMark("mark-2");
        Task otherSource = task(3L, "Student Event", TaskSourceEnum.WEB, "2026-10-30 09:00:00");
        otherSource.setMark("mark-1");
        when(taskRepository.findAll()).thenReturn(List.of(running, otherMark, otherSource));
        when(vectorService.status("1")).thenReturn("running[error]");
        when(vectorService.status("2")).thenReturn("running");
        when(vectorService.status("3")).thenReturn("running");

        TaskQueryDto query = new TaskQueryDto();
        query.setSource("system");
        query.setMark("mark-1");
        query.setNameContains("student");
        query.setStatus("running");
        TaskPageVo page = taskService.query(query);

        assertThat(page.getItems()).extracting("id").containsExactly(1L);
        assertThat(page.getItems().get(0).getFormat()).isEqualTo("yaml");
    }

    @Test
    void startIsIdempotentWhenTaskIsAlreadyRunning() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(vectorService.status("10")).thenReturn("running");

        TaskOperationVo operation = taskService.start(10L);

        assertThat(operation.isChanged()).isFalse();
        assertThat(operation.getAction()).isEqualTo("start");
        verify(vectorService, never()).startProcess(anyString());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void stopIsIdempotentAndKeepsLastRunMetadata() {
        Task task = task();
        task.setCurrentRunId("run-last");
        task.setRunStartedAt("2026-10-30T01:00:00Z");
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(vectorService.status("10")).thenReturn("stopped");

        TaskOperationVo operation = taskService.stop(10L);

        assertThat(operation.isChanged()).isFalse();
        assertThat(operation.getCurrentRunId()).isEqualTo("run-last");
        assertThat(operation.getRunStartedAt()).isEqualTo("2026-10-30T01:00:00Z");
        verify(vectorService, never()).stopProcess(anyString());
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void restartAlwaysStartsAndPublishesNewRunMetadata() {
        Task task = task();
        task.setCurrentRunId("run-old");
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(vectorService.status("10")).thenReturn("running");
        when(vectorService.stopProcess("10")).thenReturn(true);
        when(vectorService.startProcess("10")).thenReturn(4321L);
        when(vectorService.runMetadata("10")).thenReturn(
                new RunMetadata("run-new", "2026-10-30T02:00:00Z"));
        when(taskRepository.save(task)).thenReturn(task);

        TaskOperationVo operation = taskService.restart(10L);

        assertThat(operation.isChanged()).isTrue();
        assertThat(operation.getCurrentRunId()).isEqualTo("run-new");
        assertThat(operation.getPid()).isEqualTo(4321);
        verify(vectorService).stopProcess("10");
        verify(vectorService).startProcess("10");
        verify(zenvisBusinessService).reportTaskStarted(task);
    }

    @Test
    void updatePersistsExplicitFormatAndPreservesSourceAndMark() {
        Task task = task();
        task.setSource(TaskSourceEnum.API);
        task.setMark("api-mark");
        task.setFormat("yaml");
        task.setConfig("sources: {}");
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(vectorService.updateProcess(eq("10"), eq("sources: {new: true}"), any(), eq("toml")))
                .thenReturn(null);
        when(taskRepository.save(task)).thenReturn(task);
        TaskDto update = taskDto();
        update.setConfig("sources: {new: true}");
        update.setFormat("toml");

        boolean updated = taskService.update(10L, update);

        assertThat(updated).isTrue();
        assertThat(task.getFormat()).isEqualTo("toml");
        assertThat(task.getSource()).isEqualTo(TaskSourceEnum.API);
        assertThat(task.getMark()).isEqualTo("api-mark");
        verify(zenvisBusinessService).reportTaskUpdated(task);
    }

    @Test
    void legacyTaskFormatIsDerivedOnReadWithoutChangingPersistence() {
        Task legacy = task();
        legacy.setFormat(null);
        legacy.setConfig("sources:\n  demo:\n    type: demo_logs\n");
        when(taskRepository.findById(10L)).thenReturn(Optional.of(legacy));
        when(vectorService.status("10")).thenReturn("stopped");

        TaskVo taskVo = taskService.info(10L);

        assertThat(taskVo.getFormat()).isEqualTo("yaml");
        assertThat(legacy.getFormat()).isNull();
    }

    private Task task() {
        Task task = new Task();
        task.setId(10L);
        task.setName("demo-task");
        task.setPid(1234);
        task.setSource(TaskSourceEnum.SYSTEM);
        task.setMark("mark-1");
        task.setFormat("yaml");
        task.setConfig("sources: {}");
        task.setUpdateTime(java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 10, 30, 12, 0)));
        return task;
    }

    private Task task(long id, String name, TaskSourceEnum source, String updateTime) {
        Task task = task();
        task.setId(id);
        task.setName(name);
        task.setSource(source);
        task.setUpdateTime(java.sql.Timestamp.valueOf(updateTime));
        return task;
    }

    private TaskDto taskDto() {
        TaskDto taskDto = new TaskDto();
        taskDto.setName("demo-task");
        taskDto.setDescription("demo");
        taskDto.setConfig("sources: {}");
        taskDto.setFormat("yaml");
        return taskDto;
    }
}
