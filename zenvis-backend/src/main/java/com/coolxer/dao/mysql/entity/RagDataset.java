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
 * RAG数据集表
 */
@Data
@Entity
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = MysqlFinalTableName.T_RAG_DATASET)
public class RagDataset extends BaseEntity {

    /**
     * 数据集名称
     */
    @Column(nullable = false)
    private String name;

    /**
     * 来源
     */
    @Column
    private String source;

    /**
     * 标签(JSON数组字符串)
     */
    @Column(columnDefinition = "TEXT")
    private String tags;

    /**
     * 描述
     */
    @Column
    private String description;

    /**
     * MinIO存储目录前缀
     */
    @Column(nullable = false)
    private String directory;

    /**
     * 文件数量
     */
    @Column(name = "file_count")
    private Integer fileCount = 0;
}
