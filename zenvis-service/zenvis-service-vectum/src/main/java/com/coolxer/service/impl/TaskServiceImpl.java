package com.coolxer.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.TaskRepository;
import com.coolxer.model.Task;
import com.coolxer.model.dto.TaskDto;
import com.coolxer.model.vo.TaskVo;
import com.coolxer.service.TaskService;
import com.coolxer.service.VectorService;
import com.coolxer.service.ZenvisBusinessService;

/**
 * 任务服务实现类
 * <p>
 * 提供任务的增删改查、启停控制、日志查看等业务功能
 * </p>
 */
@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    private final VectorService vectorService;
    private final TaskRepository taskRepository;
    private final ZenvisBusinessService zenvisBusinessService;

    public TaskServiceImpl(VectorService vectorService,
                           TaskRepository taskRepository,
                           ZenvisBusinessService zenvisBusinessService) {
        this.vectorService = vectorService;
        this.taskRepository = taskRepository;
        this.zenvisBusinessService = zenvisBusinessService;
    }

    /**
     * 查询全部列表
     * @return 任务视图对象列表
     */
    @Override
    public List<TaskVo> findAll() {
        return taskRepository.findAll().stream()
                .map(task -> new TaskVo(task, vectorService.status(String.valueOf(task.getId()))))
                .collect(Collectors.toList());
    }

    /**
     * 创建推送任务
     * @param taskDto 传输实体
     * @return 任务视图对象
     */
    @Override
    public TaskVo create(TaskDto taskDto) {
        Task task = new Task();
        task.updateFromDto(taskDto);
        Task taskSaved = taskRepository.save(task);
        
        String errorInfo = vectorService.createProcess(
                String.valueOf(task.getId()), task.getConfig(), task.getLuaFiles());
        if (errorInfo != null) {
            taskRepository.delete(task);
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), errorInfo);
        }

        zenvisBusinessService.reportTaskCreated(taskSaved);
        return new TaskVo(taskSaved, "created");
    }

    /**
     * 修改推送任务
     * @param id 推送任务id
     * @param taskDto 用户传输实体
     * @return 是否成功
     */
    @Override
    public Boolean update(Long id, TaskDto taskDto) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with id: " + id));
        
        task.updateFromDto(taskDto);
        
        String errorInfo = vectorService.updateProcess(
                String.valueOf(task.getId()), task.getConfig(), task.getLuaFiles());
        if (errorInfo != null) {
            throw new ApiException(ResultCodeEnum.INNER_ERROR.getCode(), errorInfo);
        }
        
        Task taskSaved = taskRepository.save(task);
        zenvisBusinessService.reportTaskUpdated(taskSaved);
        return true;
    }

    /**
     * 删除推送任务
     * @param id 推送任务id
     */
    @Override
    public void delete(Long id) {
        boolean deleted = vectorService.deleteProcess(String.valueOf(id));
        if (deleted) {
            taskRepository.deleteById(id);
            zenvisBusinessService.reportTaskDeleted(id);
        }
    }

    /**
     * 批量删除
     * @param ids 任务ID列表
     */
    @Override
    public void deleteByIds(List<Long> ids) {
        ids.forEach(this::delete);
    }

    /**
     * 推送任务详情
     * @param id 推送任务id
     * @return 任务视图对象
     */
    @Override
    public TaskVo info(Long id) {
        return taskRepository.findById(id)
                .map(task -> new TaskVo(task, vectorService.status(String.valueOf(task.getId()))))
                .orElse(null);
    }

    /**
     * 启动停止任务
     * @param id 任务id
     * @return 是否成功
     */
    @Override
    public Boolean toggle(Long id) {
        Task task = taskRepository.findById(id)
                .orElse(null);
        
        if (task == null) {
            return false;
        }
        
        String status = vectorService.status(String.valueOf(task.getId()));
        
        if (status.startsWith("stopped")) {
            long pid = vectorService.startProcess(String.valueOf(task.getId()));
            if (pid > 0) {
                task.setPid((int) pid);
                taskRepository.save(task);
                zenvisBusinessService.reportTaskStarted(task);
                return true;
            }
            return false;
        } else {
            boolean stopped = vectorService.stopProcess(String.valueOf(task.getId()));
            if (stopped) {
                task.setPid(0);
                taskRepository.save(task);
                zenvisBusinessService.reportTaskStopped(task);
                return true;
            }
            return false;
        }
    }

    /**
     * 获取日志
     * @param id 任务id
     * @param logLevel 日志级别（console/system）
     * @return 日志内容
     */
    @Override
    public String log(Long id, String logLevel) {
        if ("console".equals(logLevel)) {
            return vectorService.infoLog(String.valueOf(id));
        } else if ("system".equals(logLevel)) {
            return vectorService.errorLog(String.valueOf(id));
        }
        return null;
    }
}
