package com.coolxer.service;

import com.coolxer.model.LuaFile;

import java.util.ArrayList;

/**
 * 向量服务接口
 * <p>
 * 提供向量进程的管理功能，包括创建、启动、停止、删除进程，以及日志查看等
 * </p>
 */
public interface VectorService {
    /**
     * 初始化服务
     * <p>
     * 应用启动时调用，恢复之前运行的任务进程
     * </p>
     */
    void init();

    /**
     * 创建进程配置
     * @param path 进程路径
     * @param configContext 配置内容
     * @param luaFiles Lua脚本文件列表
     * @return 错误信息，成功返回null
     */
    String createProcess(String path, String configContext, ArrayList<LuaFile> luaFiles);

    /**
     * 更新进程配置
     * @param path 进程路径
     * @param configContext 配置内容
     * @param luaFiles Lua脚本文件列表
     * @return 错误信息，成功返回null
     */
    String updateProcess(String path, String configContext, ArrayList<LuaFile> luaFiles);

    /**
     * 启动进程
     * @param path 进程路径
     * @return 进程ID
     */
    long startProcess(String path);

    /**
     * 获取进程状态
     * @param path 进程路径
     * @return 状态：stopped/running/error
     */
    String status(String path);

    /**
     * 删除进程
     * @param path 进程路径
     * @return 是否成功
     */
    boolean deleteProcess(String path);

    /**
     * 停止进程
     * @param path 进程路径
     * @return 是否成功
     */
    boolean stopProcess(String path);

    /**
     * 获取信息日志
     * @param path 进程路径
     * @return 日志内容
     */
    String infoLog(String path);

    /**
     * 获取错误日志
     * @param path 进程路径
     * @return 日志内容
     */
    String errorLog(String path);
}
