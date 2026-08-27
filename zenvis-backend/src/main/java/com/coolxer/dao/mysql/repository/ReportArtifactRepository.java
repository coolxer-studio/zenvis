package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.ReportArtifact;

import java.util.List;
import java.util.Optional;

public interface ReportArtifactRepository extends BaseRepository<ReportArtifact, Integer> {
    List<ReportArtifact> findByChatSessionIdAndCreateByOrderByCreateTimeDesc(
            Integer chatSessionId, Integer createBy);
    Optional<ReportArtifact> findByArtifactIdAndChatSessionIdAndCreateBy(
            String artifactId, Integer chatSessionId, Integer createBy);
    List<ReportArtifact> findTop100ByCreateByOrderByCreateTimeDesc(Integer createBy);
}
