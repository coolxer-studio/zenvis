package com.coolxer.service.retrieval;

import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.rule.RetrievalRule;
import com.coolxer.model.retrieval.vo.RetrievalRuleDetailVo;
import com.coolxer.model.retrieval.vo.RetrievalRuleVo;

import java.util.List;

public interface RetrievalRuleService {

    RetrievalRule getRuleById(Integer id, Integer ownerId);

    List<RetrievalRuleVo> getAllRule(Integer ownerId);

    Integer createRule(RetrievalRequestDto request, Integer ownerId);

    Integer updateRule(RetrievalRequestDto request, Integer ownerId);

    void deleteRule(Integer id, Integer ownerId);

    RetrievalRuleDetailVo getRuleDetail(Integer id, Integer ownerId);

    RetrievalRule generateRetrievalRule(RetrievalRequestDto retrievalRequestDTO);

}
