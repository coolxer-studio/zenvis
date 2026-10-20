package com.coolxer.model.dih.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 插件 08_rag/config.json 配置
 */
@Data
@NoArgsConstructor
public class PluginRagConfigDto {

    private List<Dataset> datasets;

    private List<KnowledgeBase> knowledgeBases;

    /**
     * 插件数据集声明
     */
    @Data
    @NoArgsConstructor
    public static class Dataset {

        private String code;

        private String name;

        private String source;

        private List<String> tags;

        private String description;

        private String path;
    }

    /**
     * 插件知识库声明
     */
    @Data
    @NoArgsConstructor
    public static class KnowledgeBase {

        private String code;

        private String name;

        private String source;

        private List<String> tags;

        private String description;

        private List<String> datasetCodes;
    }
}
