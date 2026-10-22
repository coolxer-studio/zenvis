package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.constant.MysqlFinalTableName;
import com.coolxer.dao.mysql.entity.ReportDraft;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReportDraftRepository extends BaseRepository<ReportDraft, Integer> {

    @Query(nativeQuery = true,
            value = "SELECT a.* FROM " + MysqlFinalTableName.T_AI_REPORT_DRAFT + " a " +
                    "WHERE a.document_id = :documentId FOR UPDATE")
    Optional<ReportDraft> findByDocumentIdForUpdate(@Param("documentId") String documentId);

    Optional<ReportDraft> findFirstByChatSessionIdAndStatusOrderByUpdateTimeDesc(
            Integer chatSessionId, String status);

    boolean existsByRunIdAndStatus(String runId, String status);
}
