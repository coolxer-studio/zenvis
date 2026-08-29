package com.coolxer.dao;

import com.coolxer.model.Task;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 任务数据访问实现类
 * <p>
 * 基于JSON文件存储的任务数据持久化实现
 * </p>
 */
@Service
public class TaskRepositoryImpl implements TaskRepository {

    @Value("${task.file}")
    private String taskFile;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong nextId = new AtomicLong(1);
    private List<Task> data = new ArrayList<>();

    public TaskRepositoryImpl() {
    }

    /**
     * 应用启动时加载数据
     * <p>
     * 从JSON文件中读取任务数据并初始化ID生成器
     * </p>
     */
    @PostConstruct
    private synchronized void loadData() {
        File file = new File(taskFile);
        if (file.exists()) {
            try {
                data = objectMapper.readValue(file, new TypeReference<List<Task>>() {});
                if (!data.isEmpty()) {
                    long maxId = data.stream().mapToLong(Task::getId).max().orElse(0L);
                    nextId.set(maxId + 1);
                }
            } catch (IOException e) {
                data = new ArrayList<>();
            }
        }
    }

    /**
     * 保存数据到文件
     * <p>
     * 将任务数据以JSON格式持久化到文件
     * </p>
     */
    private synchronized void saveData() {
        try {
            File file = new File(taskFile);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save data", e);
        }
    }

    /**
     * 查询所有任务
     * @return 任务列表
     */
    @Override
    public synchronized List<Task> findAll() {
        return data.stream()
                .sorted(Comparator.comparing(Task::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /**
     * 保存任务（新增或更新）
     * @param task 任务对象
     * @return 保存后的任务对象
     */
    @Override
    public synchronized Task save(Task task) {
        if (task.getId() == 0) {
            task.setId(nextId.getAndIncrement());
            data.add(task);
        } else {
            int index = -1;
            for (int i = 0; i < data.size(); i++) {
                if (data.get(i).getId() == task.getId()) {
                    index = i;
                    break;
                }
            }
            if (index != -1) {
                data.set(index, task);
            } else {
                data.add(task);
            }
        }
        saveData();
        return task;
    }

    /**
     * 删除任务
     * @param pushTask 要删除的任务对象
     */
    @Override
    public synchronized void delete(Task pushTask) {
        data.removeIf(task -> task.getId() == pushTask.getId());
        saveData();
    }

    /**
     * 根据ID查询任务
     * @param id 任务ID
     * @return 任务对象（可能为空）
     */
    @Override
    public synchronized Optional<Task> findById(Long id) {
        return data.stream()
                .filter(task -> task.getId() == id)
                .findFirst();
    }

    /**
     * 根据ID删除任务
     * @param id 任务ID
     */
    @Override
    public synchronized void deleteById(Long id) {
        data.removeIf(task -> task.getId() == id);
        saveData();
    }

}