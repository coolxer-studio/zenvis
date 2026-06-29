package com.coolxer.model.dih.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Skill 视图对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillVo implements Serializable {

    /**
     * Skill 唯一标识
     */
    private String id;

    /**
     * Skill 名称
     */
    private String name;

    /**
     * Skill 简介
     */
    private String description;

    /**
     * 版本号
     */
    private String version;

    /**
     * 作者
     */
    private String author;

    /**
     * 适用智能体类型。为空时表示全局可用。
     */
    @JsonAlias("agentTypes")
    private List<String> agentTypes = new ArrayList<>();

    /**
     * 标签
     */
    private List<String> tags = new ArrayList<>();

    /**
     * 是否启用
     */
    private Boolean enabled = false;

    /**
     * 入口文件，默认 SKILL.md
     */
    private String entry = "SKILL.md";

    /**
     * 相对 skill 根目录的路径
     */
    private String path;

    /**
     * 更新时间
     */
    private Date updateTime;
}
