package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.ReportRevision;

import java.util.List;
import java.util.Optional;

public interface ReportRevisionRepository extends BaseRepository<ReportRevision, Integer> {
    List<ReportRevision> findByDocumentIdOrderByRevisionDesc(String documentId);
    Optional<ReportRevision> findByDocumentIdAndRevision(String documentId, Long revision);
}
