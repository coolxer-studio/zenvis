package com.coolxer.dao;

import com.coolxer.model.Task;

import java.util.List;
import java.util.Optional;

/**
 * 任务数据访问接口
 * <p>
 * 提供任务的增删改查等基础数据操作
 * </p>
 */
public interface TaskRepository {
    /**
     * 查询所有任务
     * @return 任务列表
     */
    List<Task> findAll();

    /**
     * 保存任务（新增或更新）
     * @param task 任务对象
     * @return 保存后的任务对象
     */
    Task save(Task task);

    /**
     * 删除任务
     * @param task 要删除的任务对象
     */
    void delete(Task task);

    /**
     * 根据ID查询任务
     * @param id 任务ID
     * @return 任务对象（可能为空）
     */
    Optional<Task> findById(Long id);

    /**
     * 根据ID删除任务
     * @param id 任务ID
     */
    void deleteById(Long id);

}
