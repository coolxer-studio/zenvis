package com.coolxer.lubinsun.repository;

import com.coolxer.dao.mysql.repository.BaseRepository;
import com.coolxer.lubinsun.entity.LubinsunSkillRunEvent;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface LubinsunSkillRunEventRepository extends BaseRepository<LubinsunSkillRunEvent, Integer> {

    Optional<LubinsunSkillRunEvent> findByTaskIdAndSeq(Integer taskId, Long seq);

    List<LubinsunSkillRunEvent> findByTaskIdOrderBySeqAsc(Integer taskId);

    List<LubinsunSkillRunEvent> findByTaskIdOrderBySeqDesc(Integer taskId, Pageable pageable);
}
