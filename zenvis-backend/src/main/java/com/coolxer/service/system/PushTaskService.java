package com.coolxer.service.system;

import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.dto.PushTaskQueryDto;
import com.coolxer.model.system.dto.PushTaskValidationRequest;
import com.coolxer.model.system.validation.PushTaskDiagnosticResultVo;
import com.coolxer.model.system.validation.PushTaskValidationVo;
import com.coolxer.model.system.vo.PushTaskOperationVo;
import com.coolxer.model.system.vo.PushTaskPageVo;
import com.coolxer.model.system.vo.PushTaskVo;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface PushTaskService {

    Object proxy(HttpServletRequest request);

    boolean createAndStart(PushTaskDto pushTaskDto);

    /**
     * 创建并启动任务，返回真实任务 ID 和本轮运行信息。
     *
     * @param pushTaskDto 任务参数
     * @return 操作结果
     */
    PushTaskOperationVo createAndStartTask(PushTaskDto pushTaskDto);

    /**
     * 平台受信安装器创建并启动插件任务。
     * <p>插件包由安装流程管理，不携带智能体候选摘要；外部智能体不得使用此入口。</p>
     *
     * @param pushTaskDto 插件任务
     * @return 是否成功
     */
    default boolean createAndStartForPlugin(PushTaskDto pushTaskDto) {
        return createAndStart(pushTaskDto);
    }

    boolean updateAndStart(Integer id, PushTaskDto pushTaskDto);

    /**
     * 更新完整配置并重启任务。
     *
     * @param id 任务 ID
     * @param pushTaskDto 完整任务参数
     * @return 操作结果
     */
    PushTaskOperationVo updateAndRestartTask(Integer id, PushTaskDto pushTaskDto);

    default boolean update(Integer id, PushTaskDto pushTaskDto) {
        return false;
    }

    /**
     * 平台受信安装器更新插件任务。
     *
     * @param id 任务 ID
     * @param pushTaskDto 插件任务
     * @return 是否成功
     */
    default boolean updateForPlugin(Integer id, PushTaskDto pushTaskDto) {
        return update(id, pushTaskDto);
    }

    default boolean toggle(Integer id) {
        return false;
    }

    default boolean delete(Integer id) {
        return false;
    }

    /**
     * 按唯一任务 ID 删除任务并返回结构化结果。
     *
     * @param id 任务 ID
     * @return 操作结果
     */
    PushTaskOperationVo deleteTask(Integer id);

    List<PushTaskVo> findAll();

    /**
     * 分页查询所有来源的推送任务摘要。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PushTaskPageVo query(PushTaskQueryDto query);

    PushTaskVo findById(Integer id);

    List<PushTaskVo> findBySourceMark(String sourceMark);

    boolean deleteBySourceMark(String sourceMark);

    String getLog(Integer id, String logType);

    /**
     * 启动任务；运行中时保持现状。
     *
     * @param id 任务 ID
     * @return 操作结果
     */
    PushTaskOperationVo startTask(Integer id);

    /**
     * 停止任务；已停止时保持现状。
     *
     * @param id 任务 ID
     * @return 操作结果
     */
    PushTaskOperationVo stopTask(Integer id);

    /**
     * 重启任务并生成新的 runId。
     *
     * @param id 任务 ID
     * @return 操作结果
     */
    PushTaskOperationVo restartTask(Integer id);

    /**
     * 校验完整数据推送配置。
     *
     * @param request 校验请求
     * @return 统一校验结果
     */
    default PushTaskValidationVo validateConfig(PushTaskValidationRequest request) {
        return PushTaskValidationVo.builder()
                .passed(false)
                .blocked(true)
                .diagnostics(java.util.List.of(
                        com.coolxer.model.system.validation.PushTaskDiagnosticVo.builder()
                                .code("PUSH_VALIDATION_UNAVAILABLE")
                                .severity("ERROR")
                                .source("VECTUM")
                                .path("$")
                                .message("当前数据推送服务未实现配置校验。")
                                .repairHint("升级 Vectum 与 ZenVis 后端后重试。")
                                .build()))
                .build();
    }

    /**
     * 诊断任务当前运行轮次。
     *
     * @param id 任务 ID
     * @return 结构化诊断
     */
    default PushTaskDiagnosticResultVo diagnose(Integer id) {
        return PushTaskDiagnosticResultVo.builder()
                .verdict("UNAVAILABLE")
                .taskStatus("unknown")
                .failureStage("DIAGNOSTICS_UNAVAILABLE")
                .repairable(false)
                .build();
    }
}
