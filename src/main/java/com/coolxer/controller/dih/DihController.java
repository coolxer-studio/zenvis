package com.coolxer.controller.dih;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.dih.SuggestDto;
import com.coolxer.service.dih.AIBaseService;
import com.coolxer.service.dih.AIGeneralCompleteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数智中心
 */
@Tag(name = "数智中心")
@Slf4j
@RestController
@RequestMapping("/api/v1/dih")
public class DihController {

    @Autowired
    private AIBaseService baseService;

    @Autowired
    private AIGeneralCompleteService completeService;

    /**
     * 获取建议
     *
     * @param suggestDto
     * @return
     */
    @PostMapping(value = "/suggest")
    @Operation(summary = "补全建议", description = "补全建议")
    public ResponseWrap<String> suggest(@RequestBody SuggestDto suggestDto) {
        try {
            if (suggestDto == null || !StringUtils.hasText(suggestDto.getCurrentLine()) || suggestDto.getCurrentLine().length() < 2) {
                return ResponseWrap.success("AI暂无可用建议");
            }
            String currentLine = suggestDto.getCurrentLine().substring(0, suggestDto.getCurrentLine().length() - 2);
            String context = StringUtils.hasText(suggestDto.getContent())
                    ? suggestDto.getContent().replace(suggestDto.getCurrentLine(), currentLine)
                    : currentLine;
            String prompt = "上下文：%s\n当前行：%s\n".formatted(context, currentLine);
            String suggest = completeService.complete(prompt);
//            String prefixContent = context.substring(0, context.lastIndexOf(currentLine));
//            String suffixContent = context.substring(context.lastIndexOf(currentLine));
//            String suggest = completeService.completeCode(prefixContent, suffixContent);
            return ResponseWrap.success(suggest);
        } catch (Exception e) {
            log.warn("生成补全建议失败: {}", e.getMessage(), e);
        }
        return ResponseWrap.success("AI暂无可用建议");
    }

    @GetMapping("/model/list")
    public ResponseWrap<List<Map<String, String>>> modelList() {
        List<Map<String, String>> models = baseService.getModels();
        if (models.isEmpty()) {
            return ResponseWrap.fail(ResultCodeEnum.NO_AUTHORITY);
        }
        return ResponseWrap.success(models);
    }

    @GetMapping("/health")
    public ResponseWrap<String> health() {

        return ResponseWrap.success("is running......");
    }

}
