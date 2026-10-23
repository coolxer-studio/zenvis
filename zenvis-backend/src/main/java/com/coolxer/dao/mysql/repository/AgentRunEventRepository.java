package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.AgentRunEvent;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentRunEventRepository extends BaseRepository<AgentRunEvent, Integer> {

    List<AgentRunEvent> findTop500ByRunIdAndSequenceGreaterThanOrderBySequenceAsc(
            String runId, long afterSequence);

    List<AgentRunEvent> findTop500ByRunIdAndTypeInAndSequenceGreaterThanOrderBySequenceAsc(
            String runId, List<String> types, long afterSequence);

    Optional<AgentRunEvent> findFirstByRunIdAndTypeOrderBySequenceDesc(
            String runId, String type);

    @Query("SELECT COALESCE(MAX(e.sequence), 0) FROM AgentRunEvent e WHERE e.runId = :runId")
    long maxSequence(@Param("runId") String runId);
}
