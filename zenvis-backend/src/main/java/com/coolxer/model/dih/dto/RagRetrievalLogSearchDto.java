package com.coolxer.model.dih.dto;

import com.coolxer.model.base.dto.SortPageDto;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG检索审计日志搜索传输对象
 */
@Data
@NoArgsConstructor
public class RagRetrievalLogSearchDto extends SortPageDto {

    /**
     * 知识库ID（可选）
     */
    private Integer knowledgeBaseId;

    /**
     * 请求类型：MANUAL / CHAT（可选）
     */
    private String requestType;

    /**
     * 状态（可选）
     */
    private String status;

    /**
     * 检索请求文本模糊匹配（可选）
     */
    private String keyword;

    /**
     * 兼容 GET query/form 传参中的 knowledge_base_id。Spring MVC 的 Bean 属性绑定不走 Jackson 的 snake_case 策略。
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public void setKnowledge_base_id(Integer knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }
}
