package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.RetrievalRule;
import com.coolxer.dao.mysql.repository.RetrievalRuleRepository;
import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.coolxer.model.retrieval.dto.RequestDisplayDto;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.DataOperator;
import com.coolxer.model.retrieval.rule.DisplayAttribute;
import com.coolxer.model.retrieval.rule.RetrievalCriteriaExpression;
import com.coolxer.model.retrieval.rule.RetrievalCriteria;
import com.coolxer.model.retrieval.rule.RetrievalPageable;
import com.coolxer.model.retrieval.vo.RetrievalRuleVo;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.RetrievalRuleService;
import com.coolxer.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class RetrievalRuleServiceImpl implements RetrievalRuleService {

    @Autowired
    RetrievalRuleRepository retrievalRuleRepository;

    @Autowired
    MetaDataService metaDataService;

    private final WhereExpressionParser whereExpressionParser = new WhereExpressionParser();

    @Override
    public com.coolxer.model.retrieval.rule.RetrievalRule getRuleById(Integer id) {
        RetrievalRule retrievalRuleEntity = retrievalRuleRepository.findById(id).orElse(null);
        if (retrievalRuleEntity == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "检索规则不存在");
        }
        com.coolxer.model.retrieval.rule.RetrievalRule retrievalRule = JacksonUtil.toObject(retrievalRuleEntity.getRuleString(), com.coolxer.model.retrieval.rule.RetrievalRule.class);
        return hydrateRule(retrievalRule);
    }

    @Override
    public List<RetrievalRuleVo> getAllRule() {
        List<RetrievalRule> retrievalRuleList = retrievalRuleRepository.findAll();
        List<RetrievalRuleVo> retrievalRuleVoList = retrievalRuleList.stream().map(this::toRetrievalRuleVo).toList();
        return retrievalRuleVoList;
    }

    private RetrievalRuleVo toRetrievalRuleVo(RetrievalRule retrievalRule) {
        RetrievalRuleVo retrievalRuleVo = new RetrievalRuleVo();
        retrievalRuleVo.setName(retrievalRule.getName());
        retrievalRuleVo.setDescription(retrievalRule.getDescription());
        retrievalRuleVo.setId(retrievalRule.getId());
        retrievalRuleVo.setCreateTime(retrievalRule.getCreateTime());
        retrievalRuleVo.setUpdateTime(retrievalRule.getUpdateTime());
        return retrievalRuleVo;
    }

    @Override
    public void saveRule(com.coolxer.model.retrieval.rule.RetrievalRule retrievalRule) {
        RetrievalRule retrievalRuleEntity = new RetrievalRule();
        retrievalRuleEntity.setId(retrievalRule.getId());
        retrievalRuleEntity.setName(retrievalRule.getName());
        retrievalRuleEntity.setDescription(retrievalRule.getDescription());
        retrievalRuleEntity.setRuleString(JacksonUtil.toJson(compactRuleForPersistence(retrievalRule)));
        retrievalRuleRepository.save(retrievalRuleEntity);
    }

    private com.coolxer.model.retrieval.rule.RetrievalRule compactRuleForPersistence(com.coolxer.model.retrieval.rule.RetrievalRule retrievalRule) {
        if (retrievalRule == null) {
            return null;
        }
        com.coolxer.model.retrieval.rule.RetrievalRule compactRule = new com.coolxer.model.retrieval.rule.RetrievalRule();
        compactRule.setId(retrievalRule.getId());
        compactRule.setName(retrievalRule.getName());
        compactRule.setDescription(retrievalRule.getDescription());
        compactRule.setCriteriaLogic(retrievalRule.getCriteriaLogic());
        compactRule.setWhereExpression(retrievalRule.getWhereExpression());
        compactRule.setDisplayAttributes(compactDisplayAttributes(retrievalRule.getDisplayAttributes()));
        compactRule.setRetrievalPageable(retrievalRule.getRetrievalPageable());

        if (StringUtils.isNotBlank(retrievalRule.getWhereExpression())) {
            compactRule.setCriteriaLogic("expression");
            return compactRule;
        }

        compactRule.setRetrievalCriteria(compactRetrievalCriteria(retrievalRule.getRetrievalCriteria()));
        if (retrievalRule.getCriteriaExpression() != null) {
            compactRule.setCriteriaExpression(compactCriteriaExpression(retrievalRule.getCriteriaExpression()));
        }
        return compactRule;
    }

    private com.coolxer.model.retrieval.rule.RetrievalRule hydrateRule(com.coolxer.model.retrieval.rule.RetrievalRule retrievalRule) {
        if (retrievalRule == null) {
            return null;
        }
        retrievalRule.setDisplayAttributes(hydrateDisplayAttributes(retrievalRule.getDisplayAttributes()));
        if (StringUtils.isNotBlank(retrievalRule.getWhereExpression())) {
            String entityName = resolveRuleEntityNameForHydration(retrievalRule);
            WhereExpressionParser.WhereExpression whereExpression = whereExpressionParser.parse(retrievalRule.getWhereExpression());
            RetrievalCriteriaExpression criteriaExpression = toRetrievalCriteriaExpression(entityName, whereExpression.root(), false);
            retrievalRule.setCriteriaExpression(criteriaExpression);
            retrievalRule.setRetrievalCriteria(flattenCriteriaExpression(criteriaExpression));
            retrievalRule.setCriteriaLogic("expression");
            retrievalRule.setWhereExpression(whereExpression.normalizedExpression());
            return retrievalRule;
        }

        retrievalRule.setRetrievalCriteria(hydrateRetrievalCriteria(retrievalRule.getRetrievalCriteria(), true));
        if (retrievalRule.getCriteriaExpression() != null) {
            retrievalRule.setCriteriaExpression(hydrateCriteriaExpression(retrievalRule.getCriteriaExpression(), true));
        }
        retrievalRule.setCriteriaLogic(normalizeLogic(retrievalRule.getCriteriaLogic()));
        return retrievalRule;
    }

    @Override
    public void deleteRule(Integer id) {
        RetrievalRule retrievalRule = retrievalRuleRepository.findById(id).orElse(null);
        if (Objects.nonNull(retrievalRule)) {
            retrievalRuleRepository.delete(retrievalRule);
        } else {
            log.error("no exist rule id {}", id);
        }
    }

    @Override
    public com.coolxer.model.retrieval.rule.RetrievalRule generateRetrievalRule(RetrievalRequestDto retrievalRequestDTO) {
        if (retrievalRequestDTO == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索请求不能为空");
        }
        com.coolxer.model.retrieval.rule.RetrievalRule retrievalRule = new com.coolxer.model.retrieval.rule.RetrievalRule();

        if (StringUtils.equalsIgnoreCase(retrievalRequestDTO.getType(), "advanced") && StringUtils.isBlank(retrievalRequestDTO.getSql())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "高级where表达式不能为空");
        }
        if (StringUtils.isNotBlank(retrievalRequestDTO.getSql())) {
            WhereExpressionParser.WhereExpression whereExpression = whereExpressionParser.parse(retrievalRequestDTO.getSql());
            RetrievalCriteriaExpression criteriaExpression = toRetrievalCriteriaExpression(retrievalRequestDTO.getEntity(), whereExpression.root(), false);
            retrievalRule.setCriteriaExpression(criteriaExpression);
            retrievalRule.setCriteriaLogic("expression");
            retrievalRule.setWhereExpression(whereExpression.normalizedExpression());
            retrievalRule.setRetrievalCriteria(flattenCriteriaExpression(criteriaExpression));
        } else {
            List<RetrievalCriteria> retrievalCriteriaList = generateRetrievalCriteriaList(retrievalRequestDTO);
            retrievalRule.setRetrievalCriteria(retrievalCriteriaList);
            retrievalRule.setCriteriaLogic(normalizeLogic(retrievalRequestDTO.getCriteriaLogic()));
        }

        List<DisplayAttribute> displayAttributeList = generateDisplayColumnList(retrievalRequestDTO);
        retrievalRule.setDisplayAttributes(displayAttributeList);

        if (StringUtils.isNotBlank(retrievalRequestDTO.getRuleName())) {
            retrievalRule.setName(retrievalRequestDTO.getRuleName());
            retrievalRule.setDescription(retrievalRequestDTO.getRuleDescription());
        }
        if (Objects.nonNull(retrievalRequestDTO.getId())) {
            retrievalRule.setId(retrievalRequestDTO.getId());
        }

        RetrievalPageable pageable = generateRetrievalPageable(retrievalRequestDTO);
        retrievalRule.setRetrievalPageable(pageable);

        return retrievalRule;
    }

    private List<RetrievalCriteria> generateRetrievalCriteriaList(RetrievalRequestDto retrievalRequestDTO) {
        return generateRetrievalCriteriaList(retrievalRequestDTO.getEntity(), retrievalRequestDTO.getCriteriaList(), true);
    }

    private List<RetrievalCriteria> generateRetrievalCriteriaList(String entityName, List<RequestCriteriaDto> criteriaDtoList, boolean restrictAttributeOperators) {
        return CollectionUtils.isEmpty(criteriaDtoList) ? new ArrayList<>() :
                criteriaDtoList.stream().map(criteriaDto -> toRetrievalCriteria(entityName, criteriaDto, restrictAttributeOperators)).toList();
    }

    private RetrievalCriteriaExpression toRetrievalCriteriaExpression(String entityName, WhereExpressionParser.WhereNode whereNode, boolean restrictAttributeOperators) {
        RetrievalCriteriaExpression expression = new RetrievalCriteriaExpression();
        expression.setType(whereNode.type());
        expression.setLogic(whereNode.logic());
        if ("condition".equals(whereNode.type())) {
            expression.setCriteria(toRetrievalCriteria(entityName, whereNode.criteria(), restrictAttributeOperators));
        } else {
            expression.setChildren(whereNode.children().stream()
                    .map(child -> toRetrievalCriteriaExpression(entityName, child, restrictAttributeOperators))
                    .toList());
        }
        return expression;
    }

    private List<RetrievalCriteria> flattenCriteriaExpression(RetrievalCriteriaExpression expression) {
        if (expression == null) {
            return new ArrayList<>();
        }
        if ("condition".equals(expression.getType())) {
            return List.of(expression.getCriteria());
        }
        if (CollectionUtils.isEmpty(expression.getChildren())) {
            return new ArrayList<>();
        }
        return expression.getChildren().stream()
                .flatMap(child -> flattenCriteriaExpression(child).stream())
                .toList();
    }

    private List<RetrievalCriteria> compactRetrievalCriteria(List<RetrievalCriteria> retrievalCriteriaList) {
        if (CollectionUtils.isEmpty(retrievalCriteriaList)) {
            return new ArrayList<>();
        }
        return retrievalCriteriaList.stream()
                .map(this::compactRetrievalCriteria)
                .toList();
    }

    private RetrievalCriteria compactRetrievalCriteria(RetrievalCriteria retrievalCriteria) {
        RetrievalCriteria compactCriteria = new RetrievalCriteria();
        String entityName = retrievalCriteria.getEntity() == null ? null : retrievalCriteria.getEntity().getName();
        compactCriteria.setEntity(compactEntity(retrievalCriteria.getEntity()));
        compactCriteria.setAttribute(compactAttribute(retrievalCriteria.getAttribute(), entityName));
        compactCriteria.setOperator(compactOperator(retrievalCriteria.getOperator()));
        compactCriteria.setValueList(retrievalCriteria.getValueList());
        return compactCriteria;
    }

    private RetrievalCriteriaExpression compactCriteriaExpression(RetrievalCriteriaExpression criteriaExpression) {
        RetrievalCriteriaExpression compactExpression = new RetrievalCriteriaExpression();
        compactExpression.setType(criteriaExpression.getType());
        compactExpression.setLogic(criteriaExpression.getLogic());
        if ("condition".equals(criteriaExpression.getType())) {
            compactExpression.setCriteria(compactRetrievalCriteria(criteriaExpression.getCriteria()));
        } else if (CollectionUtils.isNotEmpty(criteriaExpression.getChildren())) {
            compactExpression.setChildren(criteriaExpression.getChildren().stream()
                    .map(this::compactCriteriaExpression)
                    .toList());
        }
        return compactExpression;
    }

    private List<DisplayAttribute> compactDisplayAttributes(List<DisplayAttribute> displayAttributes) {
        if (CollectionUtils.isEmpty(displayAttributes)) {
            return new ArrayList<>();
        }
        return displayAttributes.stream()
                .map(displayAttribute -> {
                    DisplayAttribute compactDisplayAttribute = new DisplayAttribute();
                    String entityName = displayAttribute.getEntity() == null ? null : displayAttribute.getEntity().getName();
                    compactDisplayAttribute.setEntity(compactEntity(displayAttribute.getEntity()));
                    if (CollectionUtils.isEmpty(displayAttribute.getAttributeList())) {
                        compactDisplayAttribute.setAttributeList(new ArrayList<>());
                    } else {
                        compactDisplayAttribute.setAttributeList(displayAttribute.getAttributeList().stream()
                                .map(attribute -> compactAttribute(attribute, entityName))
                                .toList());
                    }
                    return compactDisplayAttribute;
                })
                .toList();
    }

    private DataEntity compactEntity(DataEntity entity) {
        if (entity == null) {
            return null;
        }
        DataEntity compactEntity = new DataEntity();
        compactEntity.setName(entity.getName());
        return compactEntity;
    }

    private DataAttribute compactAttribute(DataAttribute attribute, String fallbackEntityName) {
        if (attribute == null) {
            return null;
        }
        DataAttribute compactAttribute = new DataAttribute();
        compactAttribute.setEntity(StringUtils.defaultIfBlank(attribute.getEntity(), fallbackEntityName));
        compactAttribute.setName(attribute.getName());
        return compactAttribute;
    }

    private DataOperator compactOperator(DataOperator operator) {
        if (operator == null) {
            return null;
        }
        DataOperator compactOperator = new DataOperator();
        compactOperator.setName(operator.getName());
        return compactOperator;
    }

    private List<RetrievalCriteria> hydrateRetrievalCriteria(List<RetrievalCriteria> retrievalCriteriaList, boolean restrictAttributeOperators) {
        if (CollectionUtils.isEmpty(retrievalCriteriaList)) {
            return new ArrayList<>();
        }
        return retrievalCriteriaList.stream()
                .map(criteria -> hydrateRetrievalCriteria(criteria, restrictAttributeOperators))
                .toList();
    }

    private RetrievalCriteria hydrateRetrievalCriteria(RetrievalCriteria retrievalCriteria, boolean restrictAttributeOperators) {
        if (retrievalCriteria == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索条件不完整");
        }
        String entityName = null;
        if (retrievalCriteria.getEntity() != null) {
            entityName = retrievalCriteria.getEntity().getName();
        }
        if (StringUtils.isBlank(entityName) && retrievalCriteria.getAttribute() != null) {
            entityName = retrievalCriteria.getAttribute().getEntity();
        }
        RequestCriteriaDto criteriaDto = new RequestCriteriaDto();
        criteriaDto.setAttribute(retrievalCriteria.getAttribute() == null ? null : retrievalCriteria.getAttribute().getName());
        criteriaDto.setOperator(retrievalCriteria.getOperator() == null ? null : retrievalCriteria.getOperator().getName());
        criteriaDto.setValueList(retrievalCriteria.getValueList());
        return toRetrievalCriteria(entityName, criteriaDto, restrictAttributeOperators);
    }

    private RetrievalCriteriaExpression hydrateCriteriaExpression(RetrievalCriteriaExpression criteriaExpression, boolean restrictAttributeOperators) {
        RetrievalCriteriaExpression hydratedExpression = new RetrievalCriteriaExpression();
        hydratedExpression.setType(criteriaExpression.getType());
        hydratedExpression.setLogic(criteriaExpression.getLogic());
        if ("condition".equals(criteriaExpression.getType())) {
            hydratedExpression.setCriteria(hydrateRetrievalCriteria(criteriaExpression.getCriteria(), restrictAttributeOperators));
        } else if (CollectionUtils.isNotEmpty(criteriaExpression.getChildren())) {
            hydratedExpression.setChildren(criteriaExpression.getChildren().stream()
                    .map(child -> hydrateCriteriaExpression(child, restrictAttributeOperators))
                    .toList());
        }
        return hydratedExpression;
    }

    private List<DisplayAttribute> hydrateDisplayAttributes(List<DisplayAttribute> displayAttributes) {
        if (CollectionUtils.isEmpty(displayAttributes)) {
            return new ArrayList<>();
        }
        return displayAttributes.stream()
                .map(this::hydrateDisplayAttribute)
                .toList();
    }

    private DisplayAttribute hydrateDisplayAttribute(DisplayAttribute displayAttribute) {
        if (displayAttribute == null || displayAttribute.getEntity() == null || StringUtils.isBlank(displayAttribute.getEntity().getName())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "展示实体不能为空");
        }
        DataEntity entity = metaDataService.getDataEntityByName(displayAttribute.getEntity().getName());
        if (entity == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "展示实体不存在: " + displayAttribute.getEntity().getName());
        }
        if (CollectionUtils.isEmpty(displayAttribute.getAttributeList())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "展示字段不能为空");
        }
        List<DataAttribute> attributeList = displayAttribute.getAttributeList().stream()
                .map(attribute -> hydrateDisplayAttribute(entity.getName(), attribute))
                .toList();
        DisplayAttribute hydratedDisplayAttribute = new DisplayAttribute();
        hydratedDisplayAttribute.setEntity(entity);
        hydratedDisplayAttribute.setAttributeList(attributeList);
        return hydratedDisplayAttribute;
    }

    private DataAttribute hydrateDisplayAttribute(String entityName, DataAttribute attribute) {
        if (attribute == null || StringUtils.isBlank(attribute.getName())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "展示字段不能为空");
        }
        DataAttribute dataAttribute = metaDataService.getDataAttributeByName(entityName, attribute.getName());
        if (dataAttribute == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "展示字段不存在: " + attribute.getName());
        }
        return dataAttribute;
    }

    private String resolveRuleEntityNameForHydration(com.coolxer.model.retrieval.rule.RetrievalRule retrievalRule) {
        if (CollectionUtils.isNotEmpty(retrievalRule.getDisplayAttributes())
                && retrievalRule.getDisplayAttributes().get(0).getEntity() != null
                && StringUtils.isNotBlank(retrievalRule.getDisplayAttributes().get(0).getEntity().getName())) {
            return retrievalRule.getDisplayAttributes().get(0).getEntity().getName();
        }
        if (CollectionUtils.isNotEmpty(retrievalRule.getRetrievalCriteria())) {
            RetrievalCriteria retrievalCriteria = retrievalRule.getRetrievalCriteria().get(0);
            if (retrievalCriteria.getEntity() != null && StringUtils.isNotBlank(retrievalCriteria.getEntity().getName())) {
                return retrievalCriteria.getEntity().getName();
            }
            if (retrievalCriteria.getAttribute() != null && StringUtils.isNotBlank(retrievalCriteria.getAttribute().getEntity())) {
                return retrievalCriteria.getAttribute().getEntity();
            }
        }
        String expressionEntityName = resolveExpressionEntityName(retrievalRule.getCriteriaExpression());
        if (StringUtils.isNotBlank(expressionEntityName)) {
            return expressionEntityName;
        }
        throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "规则缺少实体信息");
    }

    private String resolveExpressionEntityName(RetrievalCriteriaExpression criteriaExpression) {
        if (criteriaExpression == null) {
            return null;
        }
        if ("condition".equals(criteriaExpression.getType()) && criteriaExpression.getCriteria() != null) {
            RetrievalCriteria criteria = criteriaExpression.getCriteria();
            if (criteria.getEntity() != null && StringUtils.isNotBlank(criteria.getEntity().getName())) {
                return criteria.getEntity().getName();
            }
            if (criteria.getAttribute() != null && StringUtils.isNotBlank(criteria.getAttribute().getEntity())) {
                return criteria.getAttribute().getEntity();
            }
            return null;
        }
        if (CollectionUtils.isEmpty(criteriaExpression.getChildren())) {
            return null;
        }
        return criteriaExpression.getChildren().stream()
                .map(this::resolveExpressionEntityName)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private RetrievalCriteria toRetrievalCriteria(String entityName, RequestCriteriaDto criteriaDto, boolean restrictAttributeOperators) {
        if (StringUtils.isBlank(entityName)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索实体不能为空");
        }
        if (criteriaDto == null || StringUtils.isBlank(criteriaDto.getAttribute()) || StringUtils.isBlank(criteriaDto.getOperator())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索条件不完整");
        }
        DataAttribute attribute = metaDataService.getDataAttributeByName(entityName, criteriaDto.getAttribute());
        if (attribute == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "检索字段不存在: " + criteriaDto.getAttribute());
        }
        DataEntity entity = metaDataService.getDataEntityByName(attribute.getEntity());
        DataOperator operator = metaDataService.getDataOperatorByName(criteriaDto.getOperator());
        if (entity == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "检索实体不存在: " + entityName);
        }
        if (operator == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "不支持的检索操作符: " + criteriaDto.getOperator());
        }
        if (restrictAttributeOperators && (attribute.getOperators() == null || !attribute.getOperators().contains(criteriaDto.getOperator()))) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "字段不支持当前操作符: " + criteriaDto.getOperator());
        }
        List<String> valueList = normalizeValueList(criteriaDto.getOperator(), criteriaDto.getValueList());
        RetrievalCriteria retrievalCriteria = new RetrievalCriteria();
        retrievalCriteria.setAttribute(attribute);
        retrievalCriteria.setEntity(entity);
        retrievalCriteria.setOperator(operator);
        retrievalCriteria.setValueList(valueList);
        return retrievalCriteria;
    }

    private List<DisplayAttribute> generateDisplayColumnList(RetrievalRequestDto retrievalRequestDTO) {
        List<RequestDisplayDto> displayDtoList = retrievalRequestDTO.getDisplayList();
        return CollectionUtils.isEmpty(displayDtoList) ? new ArrayList<>() :
                displayDtoList.stream()
                        .map(this::toDisplayAttribute)
                        .filter(Objects::nonNull)
                        .toList();
    }

    private DisplayAttribute toDisplayAttribute(RequestDisplayDto displayDto) {
        if (displayDto == null || StringUtils.isBlank(displayDto.getEntity())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "展示实体不能为空");
        }
        DataEntity entity = metaDataService.getDataEntityByName(displayDto.getEntity());
        if (Objects.isNull(entity)) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "展示实体不存在: " + displayDto.getEntity());
        }
        if (CollectionUtils.isEmpty(displayDto.getAttributeList())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "展示字段不能为空");
        }
        List<DataAttribute> attributeList = displayDto.getAttributeList().stream()
                .map(attribute -> {
                    DataAttribute dataAttribute = metaDataService.getDataAttributeByName(displayDto.getEntity(), attribute);
                    if (dataAttribute == null) {
                        throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "展示字段不存在: " + attribute);
                    }
                    return dataAttribute;
                })
                .toList();
        DisplayAttribute displayAttribute = new DisplayAttribute();
        displayAttribute.setEntity(entity);
        displayAttribute.setAttributeList(attributeList);
        return displayAttribute;
    }

    private RetrievalPageable generateRetrievalPageable(RetrievalRequestDto retrievalRequestDto) {
        DataAttribute dataAttribute = null;
        if (StringUtils.isNotBlank(retrievalRequestDto.getEntity()) && StringUtils.isNotBlank(retrievalRequestDto.getSortBy())) {
            dataAttribute = metaDataService.getDataAttributeByName(retrievalRequestDto.getEntity(), retrievalRequestDto.getSortBy());
        }
        String sortByColumnName;
        if (dataAttribute == null) {
            // 未指定排序字段，使用默认值
            DataEntity entity = metaDataService.getDataEntityByName(retrievalRequestDto.getEntity());
            sortByColumnName = entity == null ? null : entity.getSortColumn();
        } else {
            sortByColumnName = dataAttribute.getColumnName();
        }
        return new RetrievalPageable(retrievalRequestDto.getPage(),
                retrievalRequestDto.getSize(), sortByColumnName, retrievalRequestDto.getOrder());
    }

    private String normalizeLogic(String logic) {
        return StringUtils.equalsIgnoreCase(logic, "or") ? "or" : "and";
    }

    private List<String> normalizeValueList(String operator, List<String> valueList) {
        List<String> normalized = valueList == null ? Collections.emptyList() : valueList.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .toList();
        switch (operator) {
            case "isnull", "isnotnull" -> {
                if (!normalized.isEmpty()) {
                    throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), operator + "操作符不需要值");
                }
            }
            case "between" -> {
                if (normalized.size() != 2) {
                    throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "between操作符需要两个值");
                }
            }
            case "in" -> {
                if (normalized.isEmpty()) {
                    throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "in操作符至少需要一个值");
                }
            }
            case "equal", "notequal", "match", "greatthan", "lessthan", "greatequalthan", "lessequalthan" -> {
                if (normalized.size() != 1) {
                    throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), operator + "操作符需要一个值");
                }
            }
            default -> throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "不支持的检索操作符: " + operator);
        }
        return normalized;
    }

}
