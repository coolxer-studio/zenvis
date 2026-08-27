package com.coolxer.service.retrieval;

import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.rule.RetrievalRule;
import com.coolxer.model.retrieval.vo.DataAttributeResultVo;
import com.coolxer.model.retrieval.vo.DataEntityResultVo;
import com.coolxer.model.retrieval.vo.DataListVo;
import com.coolxer.model.retrieval.vo.RetrievalRuleDetailVo;

public interface RetrievalService {

    DataListVo retrievalByCriteria(RetrievalRequestDto retrievalRequestDto);

    DataListVo retrievalByRuleId(Integer ruleId, Integer ownerId);

    DataListVo listRule(Integer ownerId);

    RetrievalRule getRule(Integer id, Integer ownerId);

    RetrievalRuleDetailVo getRuleDetail(Integer id, Integer ownerId);

    Integer createRule(RetrievalRequestDto retrievalRequestDto, Integer ownerId);

    Integer updateRule(RetrievalRequestDto retrievalRequestDto, Integer ownerId);

    Boolean deleteRule(Integer id, Integer ownerId);

    DataEntityResultVo listEntity(Integer ruleId, Integer ownerId);

    DataListVo listCandidate(Integer attributeId, String text);

    DataListVo listCandidate(String entity, String attribute, String text);

    DataAttributeResultVo listAttribute(String entity, Integer ruleId, Integer ownerId);

    DataAttributeResultVo listAttributeForDisplay(String entity, Integer ruleId, Integer ownerId);

}
