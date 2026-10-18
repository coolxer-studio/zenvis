package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.RagKnowledgeBase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * RAG知识库
 */
public interface RagKnowledgeBaseRepository extends BaseRepository<RagKnowledgeBase, Integer> {

    /**
     * 分页查询
     *
     * @param pageable 分页参数
     * @param name     名称
     * @param source   来源
     * @param tag      标签
     * @return 分页结果
     */
    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_RAG_KNOWLEDGE_BASE + " a WHERE a.is_delete = 0 AND " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) AND " +
                    "(:source IS NULL OR a.source like concat('%',:source,'%')) AND " +
                    "(:tag IS NULL OR a.tags like concat('%',:tag,'%')) " +
                    "ORDER BY a.update_time DESC",
            countQuery = "SELECT count(*) FROM " + MysqlFinalTableName.T_RAG_KNOWLEDGE_BASE + " a WHERE a.is_delete = 0 AND " +
                    "(:name IS NULL OR a.name like concat('%',:name,'%')) AND " +
                    "(:source IS NULL OR a.source like concat('%',:source,'%')) AND " +
                    "(:tag IS NULL OR a.tags like concat('%',:tag,'%')) ")
    Page<RagKnowledgeBase> findByPage(Pageable pageable, @Param("name") String name, @Param("source") String source,
                                      @Param("tag") String tag);

    /**
     * 统计同名知识库数量（名称唯一性校验）
     *
     * @param name      知识库名称
     * @param excludeId 排除的知识库ID（更新时排除自身，可为空）
     * @return 同名知识库数量
     */
    @Query(nativeQuery = true,
            value = "SELECT count(*) FROM " + MysqlFinalTableName.T_RAG_KNOWLEDGE_BASE + " a WHERE a.is_delete = 0 AND " +
                    "a.name = :name AND (:excludeId IS NULL OR a.id != :excludeId)")
    long countByName(@Param("name") String name, @Param("excludeId") Integer excludeId);
}
