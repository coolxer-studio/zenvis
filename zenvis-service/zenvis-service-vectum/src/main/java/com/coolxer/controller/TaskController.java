package com.coolxer.controller;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.dto.TaskDto;
import com.coolxer.model.dto.TaskQueryDto;
import com.coolxer.model.diagnostic.RuntimeDiagnostic;
import com.coolxer.model.validation.ConfigValidationResult;
import com.coolxer.model.validation.ValidationRequest;
import com.coolxer.model.vo.ResponseWrap;
import com.coolxer.model.vo.TaskVo;
import com.coolxer.model.vo.TaskOperationVo;
import com.coolxer.model.vo.TaskPageVo;
import com.coolxer.service.TaskService;
import com.coolxer.service.TaskValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 任务控制器
 * <p>
 * 提供任务的增删改查、启停控制、日志查看等REST API接口
 * </p>
 */
@Tag(name = "任务管理")
@Slf4j
@Validated
@RestController
@RequestMapping("/vectum/api/v1/task")
public class TaskController {

    private static final String ID_NOT_NULL = "任务ID不能为空";
    private final TaskService taskService;
    private final TaskValidationService taskValidationService;

    public TaskController(TaskService taskService,
                          TaskValidationService taskValidationService) {
        this.taskService = taskService;
        this.taskValidationService = taskValidationService;
    }

    /**
     * 使用生产同版本 Vector 隔离校验配置。
     *
     * @param request 校验请求
     * @return 校验结果
     */
    @PostMapping("/validate")
    @Operation(summary = "校验 Vector 配置", description = "在临时目录执行 Vector 原生 validate 和 test")
    public ResponseWrap<ConfigValidationResult> validate(
            @Valid @RequestBody ValidationRequest request) {
        log.info("校验 Vector 配置: format={}, testsRequired={}",
                request.getFormat(), request.isTestsRequired());
        return ResponseWrap.success(taskValidationService.validate(request));
    }

    /**
     * 创建任务
     * @param taskDto 任务数据传输对象
     * @return 任务视图对象
     */
    @PostMapping("/add")
    @Operation(summary = "创建任务", description = "创建新的任务")
    public ResponseWrap<TaskVo> add(@Valid @RequestBody TaskDto taskDto) {
        log.info("创建任务: {}", taskDto.getName());
        TaskVo taskvo = taskService.create(taskDto);
        return ResponseWrap.success(taskvo);
    }

    /**
     * 删除任务
     * @param id 任务ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除任务", description = "根据ID删除单个任务")
    public ResponseWrap<String> delete(
            @NotNull(message = ID_NOT_NULL) @PathVariable("id") Long id) {
        log.info("删除任务: id={}", id);
        taskService.delete(id);
        return ResponseWrap.success("删除成功");
    }

    /**
     * 批量删除任务
     * @param ids 任务ID列表
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    @Operation(summary = "批量删除任务", description = "批量删除多个任务")
    public ResponseWrap<String> batchDelete(@NotEmpty(message = "任务ID列表不能为空") @RequestParam("ids") List<Long> ids) {
        log.info("批量删除任务: ids={}", ids);
        taskService.deleteByIds(ids);
        return ResponseWrap.success("批量删除成功");
    }

    /**
     * 更新任务
     * @param id 任务ID
     * @param taskDto 任务数据传输对象
     * @return 更新结果
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新任务", description = "根据ID更新任务信息")
    public ResponseWrap<String> update(
            @NotNull(message = ID_NOT_NULL) @PathVariable("id") Long id,
            @Valid @RequestBody TaskDto taskDto) {
        log.info("更新任务: id={}, name={}", id, taskDto.getName());
        if (!taskService.update(id, taskDto)) {
            throw new ApiException(ResultCodeEnum.TASK_NOT_FOUND);
        }
        return ResponseWrap.success("修改成功");
    }

    /**
     * 批量更新任务
     * @param ids 任务ID列表
     * @param taskDto 任务数据传输对象
     * @return 更新结果
     */
    @PutMapping("/batch")
    @Operation(summary = "批量更新任务", description = "批量更新多个任务")
    public ResponseWrap<String> batchUpdate(
            @NotEmpty(message = "任务ID列表不能为空") @RequestParam("ids") List<Long> ids,
            @RequestBody TaskDto taskDto) {
        log.info("批量更新任务: ids={}, name={}", ids, taskDto.getName());
        int successCount = 0;
        for (Long id : ids) {
            if (taskService.update(id, taskDto)) {
                successCount++;
            }
        }
        log.info("批量更新完成: 总数={}, 成功={}, 失败={}", ids.size(), successCount, ids.size() - successCount);
        return ResponseWrap.success("批量修改成功: " + successCount + "/" + ids.size());
    }

    /**
     * 查询所有任务列表
     * @return 任务列表
     */
    @GetMapping("/all")
    @Operation(summary = "查询所有任务", description = "获取所有任务列表")
    public ResponseWrap<List<TaskVo>> listAll() {
        log.debug("查询所有任务列表");
        return ResponseWrap.success(taskService.findAll());
    }

    /**
     * 分页查询任务摘要。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/query")
    @Operation(summary = "查询任务列表", description = "按来源、备注、名称和状态分页查询任务摘要")
    public ResponseWrap<TaskPageVo> query(TaskQueryDto query) {
        return ResponseWrap.success(taskService.query(query));
    }

    /**
     * 查询任务详情
     * @param id 任务ID
     * @return 任务详情
     */
    @GetMapping("/{id}/view")
    @Operation(summary = "查询任务详情", description = "根据ID查询任务详细信息")
    public ResponseWrap<TaskVo> getById(
            @NotNull(message = ID_NOT_NULL) @PathVariable("id") Long id) {
        log.debug("查询任务详情: id={}", id);
        TaskVo taskVo = taskService.info(id);
        if (taskVo == null) {
            throw new ApiException(ResultCodeEnum.TASK_NOT_FOUND);
        }
        return ResponseWrap.success(taskVo);
    }

    /**
     * 启动/停止任务
     * @param id 任务ID
     * @return 操作结果
     */
    @PostMapping("/{id}/toggle")
    @Operation(summary = "启动/停止任务", description = "切换任务的运行状态")
    public ResponseWrap<Void> toggle(
            @NotNull(message = ID_NOT_NULL) @PathVariable("id") Long id) {
        log.info("切换任务状态: id={}", id);
        if (!taskService.toggle(id)) {
            throw new ApiException(ResultCodeEnum.TASK_OPERATION_FAILED);
        }
        return ResponseWrap.success();
    }

    /**
     * 启动任务。
     *
     * @param id 任务 ID
     * @return 操作结果
     */
    @PostMapping("/{id}/start")
    @Operation(summary = "启动任务", description = "启动已停止的任务；已运行时保持现状")
    public ResponseWrap<TaskOperationVo> start(
            @NotNull(message = ID_NOT_NULL) @PathVariable("id") Long id) {
        return ResponseWrap.success(taskService.start(id));
    }

    /**
     * 停止任务。
     *
     * @param id 任务 ID
     * @return 操作结果
     */
    @PostMapping("/{id}/stop")
    @Operation(summary = "停止任务", description = "停止运行中的任务；已停止时保持现状")
    public ResponseWrap<TaskOperationVo> stop(
            @NotNull(message = ID_NOT_NULL) @PathVariable("id") Long id) {
        return ResponseWrap.success(taskService.stop(id));
    }

    /**
     * 重启任务。
     *
     * @param id 任务 ID
     * @return 操作结果
     */
    @PostMapping("/{id}/restart")
    @Operation(summary = "重启任务", description = "重新启动任务并生成新的运行轮次")
    public ResponseWrap<TaskOperationVo> restart(
            @NotNull(message = ID_NOT_NULL) @PathVariable("id") Long id) {
        return ResponseWrap.success(taskService.restart(id));
    }

    /**
     * 获取任务日志
     * @param id 任务ID
     * @param logType 日志类型
     * @return 日志内容
     */
    @GetMapping("/{id}/log")
    @Operation(summary = "获取任务日志", description = "根据任务ID和日志类型获取日志内容")
    public String getLog(
            @NotNull(message = ID_NOT_NULL) @PathVariable("id") Long id,
            @Parameter(description = "日志类型", required = true) @RequestParam("log_type") String logType) {
        log.debug("获取任务日志: id={}, logType={}", id, logType);
        String logContent = taskService.log(id, logType);
        if (logContent == null) {
            throw new ApiException(ResultCodeEnum.INNER_ERROR);
        }
        return logContent;
    }

    /**
     * 获取任务当前运行轮次诊断。
     *
     * @param id 任务 ID
     * @return 结构化诊断
     */
    @GetMapping("/{id}/diagnostics")
    @Operation(summary = "获取运行诊断", description = "仅分析任务当前 runId 的启动日志")
    public ResponseWrap<RuntimeDiagnostic> diagnostics(
            @NotNull(message = ID_NOT_NULL) @PathVariable("id") Long id) {
        log.debug("获取任务运行诊断: id={}", id);
        return ResponseWrap.success(taskService.diagnostics(id));
    }
}
