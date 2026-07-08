package com.coolxer.lubinsun.repository;

import com.coolxer.dao.mysql.repository.BaseRepository;
import com.coolxer.lubinsun.entity.LubinsunSkillRunTask;
import com.coolxer.lubinsun.model.LubinsunTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LubinsunSkillRunTaskRepository extends BaseRepository<LubinsunSkillRunTask, Integer> {

    Optional<LubinsunSkillRunTask> findById(Integer id);

    @Query("""
            SELECT t FROM LubinsunSkillRunTask t
            WHERE (:name IS NULL OR t.name LIKE CONCAT('%', :name, '%'))
              AND (:skill IS NULL OR t.skill LIKE CONCAT('%', :skill, '%'))
              AND (:ip IS NULL OR t.ip LIKE CONCAT('%', :ip, '%'))
              AND (:status IS NULL OR t.status = :status)
              AND (:runId IS NULL OR t.runId LIKE CONCAT('%', :runId, '%'))
            ORDER BY t.updateTime DESC
            """)
    Page<LubinsunSkillRunTask> findByPage(Pageable pageable,
                                          @Param("name") String name,
                                          @Param("skill") String skill,
                                          @Param("ip") String ip,
                                          @Param("status") LubinsunTaskStatus status,
                                          @Param("runId") String runId);

    List<LubinsunSkillRunTask> findByStatusInAndRunIdIsNotNull(Collection<LubinsunTaskStatus> statuses);
}
