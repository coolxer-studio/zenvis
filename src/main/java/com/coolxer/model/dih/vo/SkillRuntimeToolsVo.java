package com.coolxer.model.dih.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
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
     * ZenVis 内置 MCP 服务 code 到工具名列表的映射。列表只包含 "*"
     * 时表示允许该服务的全部工具。
     */
    private Map<String, List<String>> local = new LinkedHashMap<>();

    /**
     * MCP 服务 code 到原始工具名列表的映射。
     */
    private Map<String, List<String>> mcp = new LinkedHashMap<>();
}
