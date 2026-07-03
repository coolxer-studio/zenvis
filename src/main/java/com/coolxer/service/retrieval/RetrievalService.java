package com.coolxer.service.retrieval;

import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.rule.RetrievalRule;
import com.coolxer.model.retrieval.vo.DataAttributeResultVo;
import com.coolxer.model.retrieval.vo.DataEntityResultVo;
import com.coolxer.model.retrieval.vo.DataListVo;

public interface RetrievalService {

    DataListVo retrievalByCriteria(RetrievalRequestDto retrievalRequestDto);

    DataListVo retrievalByRuleId(Integer ruleId);

    DataListVo listRule();

    RetrievalRule getRule(Integer id);

    Boolean createRule(RetrievalRequestDto retrievalRequestDto);

    Boolean updateRule(RetrievalRequestDto retrievalRequestDto);

    Boolean deleteRule(RetrievalRequestDto retrievalRequestDto);

    DataEntityResultVo listEntity(Integer ruleId);

    DataListVo listCandidate(Integer attributeId, String text);

    DataListVo listCandidate(String entity, String attribute, String text);

    DataAttributeResultVo listAttribute(String entity, Integer ruleId);

    DataAttributeResultVo listAttributeForDisplay(String entity, Integer ruleId);

}
