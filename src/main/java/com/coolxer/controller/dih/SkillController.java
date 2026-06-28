package com.coolxer.controller.dih;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.base.vo.SingleValueVo;
import com.coolxer.model.dih.dto.SkillSearchDto;
import com.coolxer.model.dih.vo.SkillDetailVo;
import com.coolxer.model.dih.vo.SkillVo;
import com.coolxer.service.dih.agent.skill.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI Skill 管理
 */
@Tag(name = "AI Skill 管理")
@Slf4j
@RestController
@RequestMapping("/api/v1/dih/skills")
public class SkillController {

    @Autowired
    private SkillService skillService;

    @GetMapping("/list")
    @Operation(summary = "Skill 列表", description = "分页查询已扫描到的 Skill")
    public ResponseWrap<PageRowsVo<SkillVo>> list(SkillSearchDto skillSearchDto) {
        try {
            return ResponseWrap.success(skillService.getPageList(skillSearchDto));
        } catch (Exception e) {
            log.error("查询 Skill 列表失败", e);
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/{id}/view")
    @Operation(summary = "Skill 详情", description = "查询 Skill 元数据和入口文件内容")
    public ResponseWrap<SkillDetailVo> view(@PathVariable("id") String id) {
        try {
            return ResponseWrap.success(skillService.detail(id));
        } catch (Exception e) {
            log.error("查询 Skill 详情失败, id={}", id, e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/reload")
    @Operation(summary = "重载 Skill", description = "重新扫描 app.paths.skills 目录")
    public ResponseWrap<List<SkillVo>> reload() {
        try {
            return ResponseWrap.success(skillService.reload());
        } catch (Exception e) {
            log.error("重载 Skill 失败", e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用 Skill", description = "启用指定 Skill 并写回 skill.json")
    public ResponseWrap<SkillVo> enable(@PathVariable("id") String id) {
        try {
            return ResponseWrap.success(skillService.setEnabled(id, true));
        } catch (Exception e) {
            log.error("启用 Skill 失败, id={}", id, e);
            return ResponseWrap.fail(e);
        }
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "停用 Skill", description = "停用指定 Skill 并写回 skill.json")
    public ResponseWrap<SkillVo> disable(@PathVariable("id") String id) {
        try {
            return ResponseWrap.success(skillService.setEnabled(id, false));
        } catch (Exception e) {
            log.error("停用 Skill 失败, id={}", id, e);
            return ResponseWrap.fail(e);
        }
    }

    @GetMapping("/agent/{agentType}/prompt")
    @Operation(summary = "Agent Skill Prompt", description = "查看指定 Agent 当前会加载的 Skill 提示词片段")
    public ResponseWrap<SingleValueVo> agentPrompt(@PathVariable("agentType") String agentType) {
        try {
            return ResponseWrap.success(new SingleValueVo(skillService.buildEnabledSkillPrompt(agentType)));
        } catch (Exception e) {
            log.error("查询 Agent Skill Prompt 失败, agentType={}", agentType, e);
            return ResponseWrap.fail(e);
        }
    }
}
