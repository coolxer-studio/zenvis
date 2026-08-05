package com.coolxer.service.impl;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.coolxer.dao.TaskRepository;
import com.coolxer.model.Task;
import com.coolxer.model.dto.TaskDto;
import com.coolxer.service.VectorService;
import com.coolxer.service.ZenvisBusinessService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        taskService = new TaskServiceImpl(vectorService, taskRepository, zenvisBusinessService);
    }

    @Test
    void shouldReportCreatedEventAfterTaskAndProcessAreCreated() {
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(10L);
            return task;
        });
        when(vectorService.createProcess(any(), any(), any())).thenReturn(null);

        taskService.create(taskDto());

        verify(zenvisBusinessService).reportTaskCreated(any(Task.class));
    }

    @Test
    void shouldReportUpdatedEventAfterProcessConfigurationIsUpdated() {
        Task task = task();
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(vectorService.updateProcess(any(), any(), any())).thenReturn(null);
        when(taskRepository.save(task)).thenReturn(task);

        boolean updated = taskService.update(10L, taskDto());

        assertThat(updated).isTrue();
        verify(zenvisBusinessService).reportTaskUpdated(task);
    }

    @Test
    void shouldReportDeletedEventOnlyAfterProcessIsDeleted() {
        when(vectorService.deleteProcess("10")).thenReturn(true);

        taskService.delete(10L);

        verify(taskRepository).deleteById(10L);
        verify(zenvisBusinessService).reportTaskDeleted(10L);
    }

    @Test
    void shouldNotReportDeletedEventWhenProcessDeletionFails() {
        when(vectorService.deleteProcess("10")).thenReturn(false);

        taskService.delete(10L);

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

    private Task task() {
        Task task = new Task();
        task.setId(10L);
        task.setName("demo-task");
        task.setPid(1234);
        return task;
    }

    private TaskDto taskDto() {
        TaskDto taskDto = new TaskDto();
        taskDto.setName("demo-task");
        taskDto.setDescription("demo");
        taskDto.setConfig("sources: {}");
        return taskDto;
    }
}
