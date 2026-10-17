package com.coolxer.commons.enums;

import lombok.Getter;

/**
 * RAG知识库构建状态
 */
@Getter
public enum RagKnowledgeBaseStatus {

    UNBUILD("未构建"),
    BUILDING("构建中"),
    BUILT("已构建"),
    BUILD_FAILED("构建失败");

    RagKnowledgeBaseStatus(String description) {
        this.description = description;
    }

    private final String description;
}
