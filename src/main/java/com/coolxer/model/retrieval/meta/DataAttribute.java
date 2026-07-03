package com.coolxer.model.retrieval.meta;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 *
 */

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DataAttribute {

    /**
     * 属性id，如果放数据库管理则是表主键
     */
    private int id;

    /**
     * 属性所属实体，一个属性只属于一个实体
     */
    private String entity;

    /**
     * 属性在所属实体内的唯一标识，用于前后端交互，之所以是实体内，是因为不同实体间可能有同名属性
     * 与entity字段一起，可作为联合主键
     */
    private String name;

    /**
     * 属性描述信息，用于页面额外描述内容展示
     */
    private String description;

    /**
     * 属性展示名称，用于前端表头展示
     */
    private String label;

    /**
     * 属性检索类型，前端有可能不按数据库实际存储类型传递检索条件
     */
    private String retrievalType;

    /**
     * 属性存储字段名，数据库相关
     */
    private String columnName;

    /**
     * 属性存储类型，数据库相关
     */
    private String columnType;

    /**
     * 默认选中展示
     */
    private boolean displaySelected;

    /**
     * 属性字段名
     */
    private String displayName;

    /**
     * 属性展示类型，有可能某些属性在页面需要特别的展示类型
     */
    private String displayType;

    /**
     * 属性所支持的检索条件操作符
     */
    private List<String> operators;

    /**
     * 标识该属性是否必须用数据字典的备选值
     */
    private boolean mustCandidate;

    /**
     * 标识该属性是否启用输入自动补全
     */
    private boolean autoComplete;

    /**
     * 属性字典映射关系
     */
    private Map<String, Object> mapping;

    /**
     * 标识该属性是否开启聚合链接
     */
    private boolean aggregateLink;

}
