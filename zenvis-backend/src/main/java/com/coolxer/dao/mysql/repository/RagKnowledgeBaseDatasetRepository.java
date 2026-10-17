package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.RagKnowledgeBaseDataset;

import java.util.List;

/**
 * RAG知识库与数据集关联
 */
public interface RagKnowledgeBaseDatasetRepository extends BaseRepository<RagKnowledgeBaseDataset, Integer> {

    /**
     * 根据知识库ID查询关联
     *
     * @param knowledgeBaseId 知识库ID
     * @return 关联列表
     */
    List<RagKnowledgeBaseDataset> findByKnowledgeBaseId(Integer knowledgeBaseId);

    /**
     * 根据数据集ID查询关联
     *
     * @param datasetId 数据集ID
     * @return 关联列表
     */
    List<RagKnowledgeBaseDataset> findByDatasetId(Integer datasetId);
}
