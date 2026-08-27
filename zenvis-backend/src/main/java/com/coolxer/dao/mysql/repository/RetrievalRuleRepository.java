package com.coolxer.dao.mysql.repository;

import com.coolxer.dao.mysql.entity.RetrievalRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 数据检索管理
 */
public interface RetrievalRuleRepository extends JpaRepository<RetrievalRule, Integer> {

    Optional<RetrievalRule> findByIdAndCreateBy(Integer id, Integer createBy);

    List<RetrievalRule> findAllByCreateByOrderByUpdateTimeDesc(Integer createBy);

}
