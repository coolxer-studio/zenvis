package com.coolxer.dao.mysql.repository;

import com.coolxer.commons.enums.McpToolSourceType;
import com.coolxer.dao.mysql.entity.McpToolPolicyConfig;

import java.util.List;
import java.util.Optional;

public interface McpToolPolicyConfigRepository extends BaseRepository<McpToolPolicyConfig, Integer> {
    Optional<McpToolPolicyConfig> findByToolKey(String toolKey);
    List<McpToolPolicyConfig> findBySourceTypeOrderByToolNameAsc(McpToolSourceType sourceType);
    List<McpToolPolicyConfig> findByServerId(Integer serverId);
}
