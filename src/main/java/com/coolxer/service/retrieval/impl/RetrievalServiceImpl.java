package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.DataOperator;
import com.coolxer.model.retrieval.query.DataQueryContext;
import com.coolxer.model.retrieval.rule.DisplayAttribute;
import com.coolxer.model.retrieval.rule.RetrievalCriteria;
import com.coolxer.model.retrieval.rule.RetrievalRule;
import com.coolxer.model.retrieval.vo.*;
import com.coolxer.service.retrieval.DataQueryService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.QueryEngine;
import com.coolxer.service.retrieval.RetrievalRuleService;
import com.coolxer.service.retrieval.RetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RetrievalServiceImpl implements RetrievalService {

    @Autowired
    DataQueryService dataQueryService;

    @Autowired
    RetrievalRuleService retrievalRuleService;

    @Autowired
    MetaDataService metaDataService;

    @Autowired
    QueryEngine queryEngine;

    @Autowired
    com.coolxer.service.retrieval.RetrievalAccessPolicy retrievalAccessPolicy;

    @Override
    public DataListVo retrievalByCriteria(RetrievalRequestDto retrievalRequestDto) {
        retrievalAccessPolicy.checkRead(retrievalRequestDto == null ? null : retrievalRequestDto.getEntity());
        RetrievalRule retrievalRule = retrievalRuleService.generateRetrievalRule(retrievalRequestDto);
        DataQueryContext queryContext = dataQueryService.query(retrievalRule);
        RetrievalDataListVo<Map<String, Object>> retrievalDataListVo = new RetrievalDataListVo<>();
        retrievalDataListVo.setDataList(queryContext.getResultList());
        retrievalDataListVo.setTotal(queryContext.getTotal());
        retrievalDataListVo.setToken(queryContext.getContextId());
        return retrievalDataListVo;
    }

    @Override
    public DataListVo retrievalByRuleId(Integer ruleId, Integer ownerId) {
        RetrievalRule retrievalRule = retrievalRuleService.getRuleById(ruleId, ownerId);
        retrievalAccessPolicy.checkRead(selectedEntities(retrievalRule).stream().findFirst().map(DataEntity::getName).orElse(null));
        DataQueryContext queryContext = dataQueryService.query(retrievalRule);
        RetrievalDataListVo<Map<String, Object>> retrievalDataListVo = new RetrievalDataListVo<>();
        retrievalDataListVo.setDataList(queryContext.getResultList());
        retrievalDataListVo.setTotal(queryContext.getTotal());
        retrievalDataListVo.setToken(queryContext.getContextId());
        return retrievalDataListVo;
    }

    @Override
    public DataListVo listRule(Integer ownerId) {
        List<RetrievalRuleVo> retrievalRuleVoList = retrievalRuleService.getAllRule(ownerId);
        DataListVo<RetrievalRuleVo> ruleDataListVo = new DataListVo<>();
        ruleDataListVo.setDataList(retrievalRuleVoList);
        ruleDataListVo.setTotal(BigDecimal.valueOf(retrievalRuleVoList.size()));
        return ruleDataListVo;
    }

    @Override
    public RetrievalRule getRule(Integer id, Integer ownerId) {
        return retrievalRuleService.getRuleById(id, ownerId);
    }

    @Override
    public RetrievalRuleDetailVo getRuleDetail(Integer id, Integer ownerId) {
        return retrievalRuleService.getRuleDetail(id, ownerId);
    }

    @Override
    public Integer createRule(RetrievalRequestDto retrievalRequestDto, Integer ownerId) {
        return retrievalRuleService.createRule(retrievalRequestDto, ownerId);
    }

    @Override
    public Integer updateRule(RetrievalRequestDto retrievalRequestDto, Integer ownerId) {
        return retrievalRuleService.updateRule(retrievalRequestDto, ownerId);
    }

    @Override
    public Boolean deleteRule(Integer id, Integer ownerId) {
        if (id == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索规则ID不能为空");
        }
        retrievalRuleService.deleteRule(id, ownerId);
        return true;
    }

    @Override
    public DataEntityResultVo listEntity(Integer ruleId, Integer ownerId) {

        DataEntityResultVo dataEntityResultVo = new DataEntityResultVo();

        List<DataEntity> entityList = metaDataService.getAllDataEntity();
        if (entityList.isEmpty()) {
            throw new ApiException(ResultCodeEnum.ENTITY_IS_EMPTY);
        }
        List<DataEntityVo> dataEntityVoList = entityList.stream()
                .map(this::toDataEntityVo).toList();
        dataEntityResultVo.setEntityList(dataEntityVoList);

        if (Objects.isNull(ruleId)) {
            List<String> selectedEntityList = List.of(entityList.get(0).getName());
            dataEntityResultVo.setSelectedEntity(selectedEntityList);
        } else {
            RetrievalRule retrievalRule = retrievalRuleService.getRuleById(ruleId, ownerId);
            Set<String> entitySet = selectedEntities(retrievalRule).stream()
                    .map(DataEntity::getName).collect(Collectors.toSet());
            dataEntityResultVo.setSelectedEntity(List.copyOf(entitySet));
        }

        return dataEntityResultVo;
    }

    private DataEntityVo toDataEntityVo(DataEntity dataEntity) {
        DataEntityVo dataEntityVo = new DataEntityVo();
        dataEntityVo.setName(dataEntity.getName());
        dataEntityVo.setDescription(dataEntity.getDescription());
        dataEntityVo.setLabel(dataEntity.getLabel());
        return dataEntityVo;
    }

    @Override
    public DataAttributeResultVo listAttribute(String entity, Integer ruleId, Integer ownerId) {
        DataAttributeResultVo dataAttributeResultVo = new DataAttributeResultVo();
        if (Objects.nonNull(ruleId)) {
            RetrievalRule retrievalRule = retrievalRuleService.getRuleById(ruleId, ownerId);
            DataEntity dataEntity = resolveRuleEntity(retrievalRule);
            dataAttributeResultVo.setAttributeList(generateDataAttributeVoList(dataEntity));
            dataAttributeResultVo.setSelectAttributeList(generateSelectAttributeVoList(retrievalRule));
            dataAttributeResultVo.setCriteriaLogic(StringUtils.defaultIfBlank(retrievalRule.getCriteriaLogic(), "and"));
            dataAttributeResultVo.setSql(retrievalRule.getWhereExpression());
            dataAttributeResultVo.setEntity(dataEntity.getName());
        } else if (StringUtils.isNotBlank(entity)) {
            DataEntity dataEntity = requireDataEntity(entity);
            dataAttributeResultVo.setAttributeList(generateDataAttributeVoList(dataEntity));
            dataAttributeResultVo.setEntity(dataEntity.getName());
        } else {
            log.error("invalid param");
        }
        return dataAttributeResultVo;
    }

    private List<DataAttributeVo> generateDataAttributeVoList(DataEntity dataEntity) {
        List<DataAttribute> attributeList = metaDataService.getAllDataAttributeByEntity(dataEntity);
        return attributeList.stream().map(this::toDataAttributeVo).toList();
    }

    private DataAttributeVo toDataAttributeVo(DataAttribute dataAttribute) {
        DataAttributeVo dataAttributeVo = new DataAttributeVo();
        dataAttributeVo.setName(dataAttribute.getName());
        dataAttributeVo.setLabel(dataAttribute.getLabel());
        dataAttributeVo.setDescription(dataAttribute.getDescription());
        dataAttributeVo.setLinkTemplate(dataAttribute.getLinkTemplate());
        dataAttributeVo.setAutoComplete(dataAttribute.isAutoComplete());
        if (Objects.nonNull(dataAttribute.getRetrievalType())) {
            dataAttributeVo.setRetrievalType(dataAttribute.getRetrievalType());
        }
        dataAttributeVo.setDisplayType(dataAttribute.getDisplayType());
        List<OperatorVo> operatorVoList = dataAttribute.getOperators().stream()
                .map(operator -> metaDataService.getDataOperatorByName(operator))
                .map(this::toOperatorVo).toList();
        dataAttributeVo.setOperatorList(operatorVoList);
        return dataAttributeVo;
    }

    private OperatorVo toOperatorVo(DataOperator dataOperator) {
        OperatorVo operatorVo = new OperatorVo();
        operatorVo.setName(dataOperator.getName());
        operatorVo.setLabel(dataOperator.getLabel());
        return operatorVo;
    }

    private List<SelectAttributeVo> generateSelectAttributeVoList(RetrievalRule retrievalRule) {
        List<SelectAttributeVo> selectAttributeVoList = new ArrayList<>();
        retrievalRule.getRetrievalCriteria().forEach(criteria -> {
            SelectAttributeVo selectAttributeVo = new SelectAttributeVo();
            selectAttributeVo.setName(criteria.getAttribute().getName());
            selectAttributeVo.setLabel(criteria.getAttribute().getLabel());
            selectAttributeVo.setOperatorName(criteria.getOperator().getName());
            selectAttributeVo.setLinkTemplate(criteria.getAttribute().getLinkTemplate());
            if (Objects.nonNull(criteria.getAttribute().getDisplayType())) {
                selectAttributeVo.setDisplayType(criteria.getAttribute().getDisplayType());
            }
            selectAttributeVo.setValueList(criteria.getValueList());
            selectAttributeVoList.add(selectAttributeVo);
        });
        return selectAttributeVoList;
    }

    @Override
    public DataAttributeResultVo listAttributeForDisplay(String entity, Integer ruleId, Integer ownerId) {
        DataAttributeResultVo dataAttributeResultVo = new DataAttributeResultVo();
        if (Objects.nonNull(ruleId)) {
            RetrievalRule retrievalRule = retrievalRuleService.getRuleById(ruleId, ownerId);
            DataEntity dataEntity = resolveRuleEntity(retrievalRule);
            dataAttributeResultVo.setAttributeList(generateDataAttributeVoList(dataEntity));
            dataAttributeResultVo.setSelectAttributeList(generateSelectAttributeVoListForDisplayByRule(retrievalRule));
            dataAttributeResultVo.setEntity(dataEntity.getName());
        } else if (StringUtils.isNotBlank(entity)) {
            DataEntity dataEntity = requireDataEntity(entity);
            dataAttributeResultVo.setAttributeList(generateDataAttributeVoList(dataEntity));
            dataAttributeResultVo.setSelectAttributeList(generateSelectAttributeVoListForDisplayByDefault(dataEntity));
            dataAttributeResultVo.setEntity(dataEntity.getName());
        } else {
            log.error("invalid param");
        }
        return dataAttributeResultVo;
    }

    private List<SelectAttributeVo> generateSelectAttributeVoListForDisplayByRule(RetrievalRule retrievalRule) {
        List<SelectAttributeVo> selectAttributeVoList = new ArrayList<>();
        if (CollectionUtils.isEmpty(retrievalRule.getDisplayAttributes())
                || CollectionUtils.isEmpty(retrievalRule.getDisplayAttributes().get(0).getAttributeList())) {
            return generateSelectAttributeVoListForDisplayByDefault(resolveRuleEntity(retrievalRule));
        }
        retrievalRule.getDisplayAttributes().get(0).getAttributeList().forEach(attribute -> {
            SelectAttributeVo selectAttributeVo = new SelectAttributeVo();
            selectAttributeVo.setName(attribute.getName());
            selectAttributeVo.setLabel(attribute.getLabel());
            selectAttributeVo.setLinkTemplate(attribute.getLinkTemplate());
            if (Objects.nonNull(attribute.getDisplayType())) {
                selectAttributeVo.setDisplayType(attribute.getDisplayType());
            }
            selectAttributeVoList.add(selectAttributeVo);
        });
        return selectAttributeVoList;
    }

    private List<SelectAttributeVo> generateSelectAttributeVoListForDisplayByDefault(DataEntity dataEntity) {
        List<SelectAttributeVo> selectAttributeVoList = new ArrayList<>();
        metaDataService.getAllDataAttributeByEntity(dataEntity).stream().
                filter(attribute -> attribute.isDisplaySelected()).
                forEach(attribute -> {
                    SelectAttributeVo selectAttributeVo = new SelectAttributeVo();
                    selectAttributeVo.setName(attribute.getName());
                    selectAttributeVo.setLabel(attribute.getLabel());
                    selectAttributeVo.setLinkTemplate(attribute.getLinkTemplate());
                    if (Objects.nonNull(attribute.getDisplayType())) {
                        selectAttributeVo.setDisplayType(attribute.getDisplayType());
                    }
                    selectAttributeVoList.add(selectAttributeVo);
                });
        return selectAttributeVoList;
    }

    @Override
    public DataListVo listCandidate(Integer attributeId, String text) {
        DataAttribute attribute = metaDataService.getDataAttributeById(attributeId);
        if (attribute == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "字段不存在: " + attributeId);
        }
        return listCandidate(attribute.getEntity(), attribute.getName(), text);
    }

    @Override
    public DataListVo<String> listCandidate(String entity, String attribute, String text) {
        retrievalAccessPolicy.checkRead(entity);
        DataEntity dataEntity = metaDataService.getDataEntityByName(entity);
        if (dataEntity == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "实体不存在: " + entity);
        }
        DataAttribute dataAttribute = metaDataService.getDataAttributeByName(entity, attribute);
        if (dataAttribute == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "字段不存在: " + attribute);
        }
        List<String> candidateList;
        if (StringUtils.isNotBlank(text)) {
            candidateList = queryEngine.getLike(dataEntity.getTableName(), dataAttribute.getColumnName(), text);
        } else if (StringUtils.startsWithIgnoreCase(dataAttribute.getColumnType(), "Array")) {
            candidateList = queryEngine.getDistinctForArray(dataEntity.getTableName(), dataAttribute.getColumnName());
        } else {
            candidateList = queryEngine.getDistinct(dataEntity.getTableName(), dataAttribute.getColumnName());
        }
        DataListVo<String> dataListVo = new DataListVo<>();
        dataListVo.setDataList(candidateList);
        dataListVo.setTotal(BigDecimal.valueOf(candidateList.size()));
        return dataListVo;
    }

    private List<DataEntity> selectedEntities(RetrievalRule retrievalRule) {
        if (CollectionUtils.isNotEmpty(retrievalRule.getRetrievalCriteria())) {
            return retrievalRule.getRetrievalCriteria().stream().map(RetrievalCriteria::getEntity).toList();
        }
        if (CollectionUtils.isNotEmpty(retrievalRule.getDisplayAttributes())) {
            return retrievalRule.getDisplayAttributes().stream().map(DisplayAttribute::getEntity).toList();
        }
        return Collections.emptyList();
    }

    private DataEntity resolveRuleEntity(RetrievalRule retrievalRule) {
        List<DataEntity> entities = selectedEntities(retrievalRule);
        if (entities.isEmpty()) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "规则缺少实体信息");
        }
        return entities.get(0);
    }

    private DataEntity requireDataEntity(String entity) {
        DataEntity dataEntity = metaDataService.getDataEntityByName(entity);
        if (dataEntity == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "实体不存在: " + entity);
        }
        return dataEntity;
    }

}
