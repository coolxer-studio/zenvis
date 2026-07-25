package com.coolxer.model.dih.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 工具白名单。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillRuntimeToolsVo implements Serializable {

    /**
     * ZenVis 本地工具名。
     */
    private List<String> local = new ArrayList<>();

    /**
     * MCP 服务 code 到原始工具名列表的映射。
     */
    private Map<String, List<String>> mcp = new LinkedHashMap<>();
}
