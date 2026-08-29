package com.coolxer.model;

import com.coolxer.commons.enums.TaskSourceEnum;
import com.coolxer.model.dto.TaskDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务实体测试
 */
class TaskTest {

    @Test
    void shouldPreserveMarkWhenUpdateDoesNotContainMark() {
        Task task = taskWithMark("原有备注");
        TaskDto taskDto = updateDto();

        task.updateFromDto(taskDto);

        assertThat(task.getMark()).isEqualTo("原有备注");
    }

    @Test
    void shouldClearMarkWhenUpdateContainsEmptyMark() {
        Task task = taskWithMark("原有备注");
        TaskDto taskDto = updateDto();
        taskDto.setMark("");

        task.updateFromDto(taskDto);

        assertThat(task.getMark()).isEmpty();
    }

    private Task taskWithMark(String mark) {
        Task task = new Task();
        task.setMark(mark);
        return task;
    }

    private TaskDto updateDto() {
        TaskDto taskDto = new TaskDto();
        taskDto.setName("更新后的任务");
        taskDto.setDescription("更新后的描述");
        taskDto.setConfig("sources: {}\nsinks: {}");
        taskDto.setSource(TaskSourceEnum.WEB.name());
        return taskDto;
    }
}
