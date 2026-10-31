package com.coolxer.controller;

import com.coolxer.model.dto.TaskQueryDto;
import com.coolxer.model.vo.ResponseWrap;
import com.coolxer.model.vo.TaskOperationVo;
import com.coolxer.model.vo.TaskPageVo;
import com.coolxer.service.TaskService;
import com.coolxer.service.TaskValidationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskControllerTest {

    private final TaskService taskService = mock(TaskService.class);
    private final TaskValidationService validationService = mock(TaskValidationService.class);
    private final TaskController controller = new TaskController(taskService, validationService);

    @Test
    void queryUsesPagedTaskContract() {
        TaskPageVo page = TaskPageVo.builder().total(3).offset(1).limit(2).hasMore(false).build();
        when(taskService.query(any(TaskQueryDto.class))).thenReturn(page);

        ResponseWrap<TaskPageVo> response = controller.query(new TaskQueryDto());

        assertThat(response.getData()).isSameAs(page);
        verify(taskService).query(any(TaskQueryDto.class));
    }

    @Test
    void lifecycleEndpointsReturnStructuredOperation() {
        TaskOperationVo started = TaskOperationVo.builder()
                .action("start").taskId(12L).status("running").changed(true).build();
        TaskOperationVo stopped = TaskOperationVo.builder()
                .action("stop").taskId(12L).status("stopped").changed(true).build();
        TaskOperationVo restarted = TaskOperationVo.builder()
                .action("restart").taskId(12L).status("running").changed(true).build();
        when(taskService.start(12L)).thenReturn(started);
        when(taskService.stop(12L)).thenReturn(stopped);
        when(taskService.restart(12L)).thenReturn(restarted);

        assertThat(controller.start(12L).getData()).isSameAs(started);
        assertThat(controller.stop(12L).getData()).isSameAs(stopped);
        assertThat(controller.restart(12L).getData()).isSameAs(restarted);
    }
}
