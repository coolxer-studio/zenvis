package com.coolxer.dao.mysql.entity;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * RAG知识库与数据集关联表
 */
@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = MysqlFinalTableName.T_RAG_KNOWLEDGE_BASE_DATASET)
public class RagKnowledgeBaseDataset extends BaseEntity {

    /**
     * 知识库ID
     */
    @Column(name = "knowledge_base_id", nullable = false)
    private Integer knowledgeBaseId;

    /**
     * 数据集ID
     */
    @Column(name = "dataset_id", nullable = false)
    private Integer datasetId;
}
