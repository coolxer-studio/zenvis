package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.AnalysisTask;
import com.coolxer.dao.mysql.repository.AnalysisTaskRepository;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.AnalysisTaskDto;
import com.coolxer.model.system.dto.AnalysisTaskSearchDto;
import com.coolxer.model.system.vo.AnalysisTaskQueueVo;
import com.coolxer.model.system.vo.AnalysisTaskVo;
import com.coolxer.service.dih.AIBaseService;
import com.coolxer.service.dih.agent.skill.BuiltinAgentSkillRegistry;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.agent.nl2sql.service.LlmService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.dih.mcp.McpToolContext;
import com.coolxer.service.system.AnalysisTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI分析任务服务
 */
@Slf4j
@Service
public class AnalysisTaskServiceImpl implements AnalysisTaskService {

    private static final String ANALYSIS_SYSTEM_PROMPT = """
            你是 ZenVis 的数据分析任务 Agent。请基于用户提供的任务提示词完成分析，输出结构清晰、结论明确的中文结果。
            如果提示词中包含数据、SQL结果、指标或上下文，请优先围绕这些信息分析；不要编造不存在的数据。
            输出建议包含：关键结论、过程说明、风险或异常点、下一步建议。
            """;

    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;

    private final AtomicBoolean executing = new AtomicBoolean(false);

    @Autowired
    private AnalysisTaskRepository analysisTaskRepository;

    @Autowired
    private LlmService llmService;

    @Autowired
    private AIBaseService aiBaseService;

    @Autowired
    private AgentMcpToolService agentMcpToolService;

    @Autowired
    private SkillService skillService;

    @Override
    public List<AnalysisTaskVo> findAll() {
        return analysisTaskRepository.findAll().stream().map(AnalysisTaskVo::new).toList();
    }

    @Override
    public PageRowsVo<AnalysisTaskVo> getPageList(AnalysisTaskSearchDto analysisTaskSearchDto) {
        try {
            Pageable pageable = PageRequest.of(analysisTaskSearchDto.getPage() - 1, analysisTaskSearchDto.getPerPage());
            Page<AnalysisTask> byPage = analysisTaskRepository.findByPage(
                    pageable,
                    blankToNull(analysisTaskSearchDto.getName()),
                    analysisTaskSearchDto.getStatus(),
                    blankToNull(analysisTaskSearchDto.getModel())
            );
            return new PageRowsVo<>(
                    byPage.getContent().stream().map(AnalysisTaskVo::new).toList(),
                    byPage.getTotalElements()
            );
        } catch (Exception e) {
            log.error("分页查询AI分析任务失败", e);
            return new PageRowsVo<>(Collections.emptyList(), 0L);
        }
    }

    @Override
    public AnalysisTask create(AnalysisTaskDto analysisTaskDto) {
        checkCreateOrUpdate(analysisTaskDto);
        AnalysisTask analysisTask = new AnalysisTask();
        analysisTask.updateFromDto(analysisTaskDto);
        analysisTask.setStatus(AnalysisTaskStatus.PENDING);
        analysisTask.setPriority(defaultPriority(analysisTask.getPriority()));
        analysisTask.setRunCount(0);
        return analysisTaskRepository.save(analysisTask);
    }

    @Override
    public Boolean update(Long id, AnalysisTaskDto analysisTaskDto) {
        checkCreateOrUpdate(analysisTaskDto);
        try {
            Optional<AnalysisTask> optionalAnalysisTask = analysisTaskRepository.findById(id);
            if (optionalAnalysisTask.isPresent()) {
                AnalysisTask analysisTask = optionalAnalysisTask.get();
                checkNotRunning(analysisTask, "执行中的分析任务不能修改");
                analysisTask.updateFromDto(analysisTaskDto);
                analysisTask.setPriority(defaultPriority(analysisTask.getPriority()));
                analysisTaskRepository.save(analysisTask);
                return true;
            }
            return false;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新AI分析任务失败, id: {}", id, e);
            return false;
        }
    }

    @Override
    public void delete(Long id) {
        AnalysisTask analysisTask = analysisTaskRepository.findById(id).orElse(null);
        if (analysisTask == null) {
            return;
        }
        checkNotRunning(analysisTask, "执行中的分析任务不能删除");
        analysisTaskRepository.deleteById(id);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        for (Long id : ids) {
            delete(id);
        }
    }

    @Override
    public AnalysisTaskVo info(Long id) {
        try {
            return analysisTaskRepository.findById(id).map(AnalysisTaskVo::new).orElse(null);
        } catch (Exception e) {
            log.error("获取AI分析任务失败, id: {}", id, e);
            return null;
        }
    }

    @Override
    public AnalysisTaskVo enqueue(Long id) {
        AnalysisTask analysisTask = analysisTaskRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "分析任务不存在"));
        checkNotRunning(analysisTask, "执行中的分析任务不能重新入队");
        analysisTask.setStatus(AnalysisTaskStatus.PENDING);
        analysisTask.setResult(null);
        analysisTask.setErrorMessage(null);
        analysisTask.setStartTime(null);
        analysisTask.setFinishTime(null);
        return new AnalysisTaskVo(analysisTaskRepository.save(analysisTask));
    }

    @Override
    public AnalysisTaskVo cancel(Long id) {
        AnalysisTask analysisTask = analysisTaskRepository.findById(id)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "分析任务不存在"));
        checkNotRunning(analysisTask, "执行中的分析任务不能取消");
        analysisTask.setStatus(AnalysisTaskStatus.CANCELED);
        analysisTask.setFinishTime(new Date());
        return new AnalysisTaskVo(analysisTaskRepository.save(analysisTask));
    }

    @Override
    public AnalysisTaskVo executeNextTask() {
        if (!executing.compareAndSet(false, true)) {
            log.debug("AI分析任务队列正在执行，本次调度跳过");
            return null;
        }

        try {
            if (analysisTaskRepository.countByStatus(AnalysisTaskStatus.RUNNING) > 0) {
                log.debug("存在执行中的AI分析任务，本次调度跳过");
                return null;
            }

            Optional<AnalysisTask> optionalAnalysisTask = analysisTaskRepository.findNextReadyTask(new Date());
            if (optionalAnalysisTask.isEmpty()) {
                log.debug("暂无到期可执行的AI分析任务");
                return null;
            }

            AnalysisTask analysisTask = startTask(optionalAnalysisTask.get());
            try {
                String result = callAiAnalyze(analysisTask);
                analysisTask.setStatus(AnalysisTaskStatus.SUCCESS);
                analysisTask.setResult(result);
                analysisTask.setErrorMessage(null);
                analysisTask.setFinishTime(new Date());
                log.info("AI分析任务执行完成, id: {}, name: {}", analysisTask.getId(), analysisTask.getName());
            } catch (Exception e) {
                analysisTask.setStatus(AnalysisTaskStatus.FAILED);
                analysisTask.setErrorMessage(resolveErrorMessage(e));
                analysisTask.setFinishTime(new Date());
                log.error("AI分析任务执行失败, id: {}, name: {}", analysisTask.getId(), analysisTask.getName(), e);
            }
            return new AnalysisTaskVo(analysisTaskRepository.save(analysisTask));
        } finally {
            executing.set(false);
        }
    }

    @Override
    public AnalysisTaskQueueVo queueStatus() {
        Date now = new Date();
        AnalysisTaskVo runningTask = analysisTaskRepository.findFirstByStatusOrderByStartTimeAsc(AnalysisTaskStatus.RUNNING)
                .map(AnalysisTaskVo::new)
                .orElse(null);
        AnalysisTaskVo nextTask = analysisTaskRepository.findNextPendingTask()
                .map(AnalysisTaskVo::new)
                .orElse(null);
        return new AnalysisTaskQueueVo(
                runningTask,
                nextTask,
                analysisTaskRepository.countByStatus(AnalysisTaskStatus.PENDING),
                analysisTaskRepository.countReadyTasks(AnalysisTaskStatus.PENDING, now),
                analysisTaskRepository.countByStatus(AnalysisTaskStatus.RUNNING),
                now
        );
    }

    @Override
    public void recoverRunningTasks() {
        List<AnalysisTask> runningTasks = analysisTaskRepository.findByStatus(AnalysisTaskStatus.RUNNING);
        if (runningTasks.isEmpty()) {
            return;
        }
        Date now = new Date();
        runningTasks.forEach(analysisTask -> {
            analysisTask.setStatus(AnalysisTaskStatus.FAILED);
            analysisTask.setErrorMessage("服务启动时发现任务处于执行中状态，已标记为失败，可重新入队执行。");
            analysisTask.setFinishTime(now);
        });
        analysisTaskRepository.saveAll(runningTasks);
        log.warn("已恢复 {} 个未完成的AI分析任务", runningTasks.size());
    }

    private AnalysisTask startTask(AnalysisTask analysisTask) {
        analysisTask.setStatus(AnalysisTaskStatus.RUNNING);
        analysisTask.setStartTime(new Date());
        analysisTask.setFinishTime(null);
        analysisTask.setErrorMessage(null);
        analysisTask.setRunCount(defaultRunCount(analysisTask.getRunCount()) + 1);
        AnalysisTask savedTask = analysisTaskRepository.save(analysisTask);
        log.info("开始执行AI分析任务, id: {}, name: {}", savedTask.getId(), savedTask.getName());
        return savedTask;
    }

    private String callAiAnalyze(AnalysisTask analysisTask) {
        String model = aiBaseService.resolveChatModel(analysisTask.getModel(), false, false);
        McpToolContext mcpToolContext = agentMcpToolService.resolve("agent_analysis");
        try {
            llmService.setModel(model);
            llmService.setMcpToolContext(mcpToolContext);
            return llmService.callWithSystemPrompt(buildAnalysisSystemPrompt(), buildAnalyzePrompt(analysisTask));
        } finally {
            llmService.clearModel();
            llmService.clearMcpToolContext();
        }
    }

    private String buildAnalysisSystemPrompt() {
        String skillPrompt = skillService.buildEnabledSkillPrompt(BuiltinAgentSkillRegistry.AGENT_ANALYSIS);
        if (StringUtils.isBlank(skillPrompt)) {
            return ANALYSIS_SYSTEM_PROMPT;
        }
        return ANALYSIS_SYSTEM_PROMPT + "\n\n【已加载 Skill】\n" + skillPrompt;
    }

    private String buildAnalyzePrompt(AnalysisTask analysisTask) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("任务名称：").append(analysisTask.getName()).append("\n");
        if (StringUtils.isNotBlank(analysisTask.getDescription())) {
            promptBuilder.append("任务描述：").append(analysisTask.getDescription()).append("\n");
        }
        promptBuilder.append("分析提示词：\n").append(analysisTask.getPrompt());
        return promptBuilder.toString();
    }

    private static void checkCreateOrUpdate(AnalysisTaskDto analysisTaskDto) {
        if (analysisTaskDto == null
                || StringUtils.isBlank(analysisTaskDto.getName())
                || StringUtils.isBlank(analysisTaskDto.getPrompt())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY);
        }
    }

    private static void checkNotRunning(AnalysisTask analysisTask, String message) {
        if (analysisTask.getStatus() != null && analysisTask.getStatus().isRunning()) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), message);
        }
    }

    private static Integer defaultPriority(Integer priority) {
        return priority == null ? 0 : priority;
    }

    private static Integer defaultRunCount(Integer runCount) {
        return runCount == null ? 0 : runCount;
    }

    private static String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    private static String resolveErrorMessage(Exception e) {
        String message = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
        if (message.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }
        return message;
    }
}
