package com.coolxer.service.impl;

import java.util.Map;

import com.coolxer.zenvis.businessservice.BusinessServiceEventSeverity;
import com.coolxer.zenvis.businessservice.BusinessServiceReporter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.coolxer.commons.enums.TaskSourceEnum;
import com.coolxer.model.Task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Zenvis 业务服务通信实现测试。
 */
class ZenvisBusinessServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldMapTaskLifecycleEventToZenvisReporter() {
        BusinessServiceReporter reporter = mock(BusinessServiceReporter.class);
        ZenvisBusinessServiceImpl service = new ZenvisBusinessServiceImpl(reporter);
        Task task = task();
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);

        service.reportTaskStarted(task);

        verify(reporter).reportEvent(
                eq("TASK_STARTED"),
                eq(BusinessServiceEventSeverity.INFO),
                eq("Vectum 任务已启动"),
                eq("Vectum 任务已启动: taskId=42"),
                eq(null),
                dataCaptor.capture());
        assertThat(dataCaptor.getValue())
                .containsEntry("taskId", 42L)
                .containsEntry("taskName", "demo-task")
                .containsEntry("taskSource", "API")
                .containsEntry("processId", 1234);
    }

    @Test
    void shouldNotPropagateReporterFailureToBusinessOperation() {
        BusinessServiceReporter reporter = mock(BusinessServiceReporter.class);
        ZenvisBusinessServiceImpl service = new ZenvisBusinessServiceImpl(reporter);
        doThrow(new IllegalStateException("zenvis unavailable"))
                .when(reporter)
                .reportEvent(any(), any(), any(), any(), any(), any());

        assertThatCode(() -> service.reportTaskCreated(task())).doesNotThrowAnyException();
    }

    private Task task() {
        Task task = new Task();
        task.setId(42L);
        task.setName("demo-task");
        task.setSource(TaskSourceEnum.API);
        task.setPid(1234);
        return task;
    }
}
