package com.coolxer.service;

import com.coolxer.model.Task;
import com.coolxer.model.dto.TaskDto;
import com.coolxer.model.vo.TaskVo;

import java.util.List;

/**
 * 任务服务接口
 * <p>
 * 提供任务的增删改查、启停控制、日志查看等业务功能
 * </p>
 */
public interface TaskService {
    /**
     * 查询全部列表
     * @return 任务视图对象列表
     */
    List<TaskVo> findAll();

    /**
     * 创建推送任务
     * @param taskDto 传输实体
     * @return 任务视图对象
     */
    TaskVo create(TaskDto taskDto);

    /**
     * 修改推送任务
     * @param id 推送任务id
     * @param taskDto 用户传输实体
     * @return 是否成功
     */
    Boolean update(Long id, TaskDto taskDto);

    /**
     * 删除推送任务
     * @param id 推送任务id
     */
    void delete(Long id);

    /**
     * 批量删除
     * @param ids 任务ID列表
     */
    void deleteByIds(List<Long> ids);

    /**
     * 推送任务详情
     * @param id 推送任务id
     * @return 任务视图对象
     */
    TaskVo info(Long id);

    /**
     * 启动停止任务
     * @param id 任务id
     * @return 是否成功
     */
    Boolean toggle(Long id);

    /**
     * 获取日志
     * @param id 任务id
     * @param logLevel 日志级别（console/system）
     * @return 日志内容
     */
    String log(Long id, String logLevel);

}