package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.McpServerConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface McpServerConfigRepository extends BaseRepository<McpServerConfig, Integer> {

    Optional<McpServerConfig> findById(Integer id);

    Optional<McpServerConfig> findByCode(String code);

    List<McpServerConfig> findBySource(String source);

    List<McpServerConfig> findByEnabledTrueOrderByIdAsc();

    @Query("""
            SELECT m FROM McpServerConfig m
            WHERE (:keyword IS NULL
                OR LOWER(m.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(m.baseUrl) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:enabled IS NULL OR m.enabled = :enabled)
              AND (:connected IS NULL OR m.connected = :connected)
            ORDER BY m.id DESC
            """)
    Page<McpServerConfig> findByPage(Pageable pageable,
                                     @Param("keyword") String keyword,
                                     @Param("enabled") Boolean enabled,
                                     @Param("connected") Boolean connected);
}
