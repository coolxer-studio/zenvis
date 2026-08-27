package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.ReportDocument;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ReportDocumentRepository extends BaseRepository<ReportDocument, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReportDocument> findByDocumentIdAndChatSessionId(
            String documentId, Integer chatSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReportDocument> findFirstByChatSessionIdOrderByUpdateTimeDesc(
            Integer chatSessionId);

    Optional<ReportDocument> findFirstByChatSessionIdAndCreateByOrderByUpdateTimeDesc(
            Integer chatSessionId, Integer createBy);

    List<ReportDocument> findTop50ByCreateByOrderByUpdateTimeDesc(Integer createBy);
}
