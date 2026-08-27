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
import com.coolxer.model.retrieval.rule.PersistedRetrievalRule;
import com.coolxer.model.retrieval.rule.RetrievalCriteriaExpression;
import com.coolxer.model.retrieval.rule.RetrievalCriteria;
import com.coolxer.model.retrieval.rule.RetrievalPageable;
import com.coolxer.model.retrieval.vo.*;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class RetrievalRuleServiceImpl implements RetrievalRuleService {

    @Autowired
    RetrievalRuleRepository retrievalRuleRepository;

    @Autowired
    MetaDataService metaDataService;

    private final WhereExpressionParser whereExpressionParser = new WhereExpressionParser();

    @Override
    public com.coolxer.model.retrieval.rule.RetrievalRule getRuleById(Integer id, Integer ownerId) {
        RetrievalRule entity = requireOwnedRule(id, ownerId);
        RetrievalRequestDto config = readStoredConfig(entity);
        if ("legacy_sql".equals(config.getType())) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "旧自由SQL检索规则已禁用，请编辑后保存");
        }
        com.coolxer.model.retrieval.rule.RetrievalRule rule = generateRetrievalRule(config);
        rule.setId(entity.getId());
        rule.setName(entity.getName());
        rule.setDescription(entity.getDescription());
        return rule;
    }

    @Override
    public List<RetrievalRuleVo> getAllRule(Integer ownerId) {
        requireOwner(ownerId);
        return retrievalRuleRepository.findAllByCreateByOrderByUpdateTimeDesc(ownerId).stream()
                .map(this::toRetrievalRuleVo)
                .toList();
    }

    private RetrievalRuleVo toRetrievalRuleVo(RetrievalRule retrievalRule) {
        RetrievalRuleVo retrievalRuleVo = new RetrievalRuleVo();
        retrievalRuleVo.setName(retrievalRule.getName());
        retrievalRuleVo.setDescription(retrievalRule.getDescription());
        retrievalRuleVo.setId(retrievalRule.getId());
        retrievalRuleVo.setCreateTime(retrievalRule.getCreateTime());
        retrievalRuleVo.setUpdateTime(retrievalRule.getUpdateTime());
        List<RetrievalRuleIssueVo> issues = validateStoredConfig(readStoredConfig(retrievalRule));
        retrievalRuleVo.setStatus(issues.isEmpty() ? "valid" : "invalid");
        retrievalRuleVo.setIssueCount(issues.size());
        return retrievalRuleVo;
    }

    @Override
    public Integer createRule(RetrievalRequestDto request, Integer ownerId) {
        requireOwner(ownerId);
        validateCreateRequest(request);
        com.coolxer.model.retrieval.rule.RetrievalRule generated = generateRetrievalRule(request);
        RetrievalRule entity = new RetrievalRule();
        entity.setName(request.getRuleName().trim());
        entity.setDescription(request.getRuleDescription());
        entity.setCreateBy(ownerId);
        entity.setUpdateBy(ownerId);
        entity.setRuleString(JacksonUtil.toJson(toPersistedRule(request, generated)));
        return retrievalRuleRepository.save(entity).getId();
    }

    @Override
    public Integer updateRule(RetrievalRequestDto request, Integer ownerId) {
        requireOwner(ownerId);
        if (request == null || request.getId() == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索规则ID不能为空");
        }
        RetrievalRule entity = requireOwnedRule(request.getId(), ownerId);
        RetrievalRequestDto previous = readStoredConfig(entity);
        RetrievalRequestDto merged = mergeForUpdate(previous, request);
        com.coolxer.model.retrieval.rule.RetrievalRule generated = generateRetrievalRule(merged);
        if (StringUtils.isNotBlank(request.getRuleName())) {
            entity.setName(request.getRuleName().trim());
        }
        if (request.getRuleDescription() != null) {
            entity.setDescription(request.getRuleDescription());
        }
        entity.setUpdateBy(ownerId);
        entity.setRuleString(JacksonUtil.toJson(toPersistedRule(merged, generated)));
        return retrievalRuleRepository.save(entity).getId();
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
    public void deleteRule(Integer id, Integer ownerId) {
        retrievalRuleRepository.delete(requireOwnedRule(id, ownerId));
    }

    @Override
    public RetrievalRuleDetailVo getRuleDetail(Integer id, Integer ownerId) {
        RetrievalRule entity = requireOwnedRule(id, ownerId);
        RetrievalRequestDto config = readStoredConfig(entity);
        List<RetrievalRuleIssueVo> issues = validateStoredConfig(config);
        RetrievalRuleDetailVo detail = new RetrievalRuleDetailVo();
        detail.setId(entity.getId());
        detail.setName(entity.getName());
        detail.setDescription(entity.getDescription());
        detail.setCreateTime(entity.getCreateTime());
        detail.setUpdateTime(entity.getUpdateTime());
        detail.setConfig(toConfigVo(config));
        detail.setIssues(issues);
        detail.setStatus(issues.isEmpty() ? "valid" : "invalid");
        detail.setEntityList(metaDataService.getAllDataEntity().stream().map(this::toDataEntityVo).toList());
        DataEntity selectedEntity = metaDataService.getDataEntityByName(config.getEntity());
        detail.setAttributeList(selectedEntity == null ? List.of() : metaDataService.getAllDataAttributeByEntity(selectedEntity)
                .stream().map(this::toDataAttributeVo).toList());
        return detail;
    }

    private RetrievalRule requireOwnedRule(Integer id, Integer ownerId) {
        requireOwner(ownerId);
        if (id == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索规则ID不能为空");
        }
        return retrievalRuleRepository.findByIdAndCreateBy(id, ownerId)
                .orElseThrow(() -> new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "检索规则不可用"));
    }

    private void requireOwner(Integer ownerId) {
        if (ownerId == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "当前用户未登录");
        }
    }

    private void validateCreateRequest(RetrievalRequestDto request) {
        if (request == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索规则不能为空");
        }
        if (StringUtils.isBlank(request.getRuleName())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索规则名称不能为空");
        }
        if (StringUtils.isBlank(request.getEntity())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索实体不能为空");
        }
        if (!StringUtils.equalsAnyIgnoreCase(request.getType(), "normal", "advanced")) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "检索类型仅支持normal或advanced");
        }
        if (CollectionUtils.isEmpty(request.getDisplayList())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "展示字段不能为空");
        }
    }

    private RetrievalRequestDto mergeForUpdate(RetrievalRequestDto previous, RetrievalRequestDto incoming) {
        RetrievalRequestDto merged = new RetrievalRequestDto();
        String previousEntity = previous.getEntity();
        String entity = StringUtils.defaultIfBlank(incoming.getEntity(), previousEntity);
        String type = StringUtils.defaultIfBlank(incoming.getType(), previous.getType());
        boolean entityChanged = !Objects.equals(previousEntity, entity);
        boolean typeChanged = incoming.getType() != null && !StringUtils.equalsIgnoreCase(previous.getType(), type);

        if (entityChanged) {
            if (incoming.getDisplayList() == null) {
                throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "切换实体时必须显式提交展示字段");
            }
            if (StringUtils.equalsIgnoreCase(type, "advanced") && StringUtils.isBlank(incoming.getSql())) {
                throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "切换实体时必须显式提交高级where表达式");
            }
            if (StringUtils.equalsIgnoreCase(type, "normal") && incoming.getCriteriaList() == null) {
                throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "切换实体时必须显式提交普通检索条件");
            }
        }
        if (typeChanged && StringUtils.equalsIgnoreCase(type, "advanced") && StringUtils.isBlank(incoming.getSql())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "切换为高级检索时必须提交where表达式");
        }
        if (typeChanged && StringUtils.equalsIgnoreCase(type, "normal") && incoming.getCriteriaList() == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "切换为普通检索时必须提交检索条件");
        }

        merged.setId(incoming.getId());
        merged.setType(type);
        merged.setEntity(entity);
        merged.setCriteriaLogic(incoming.getCriteriaLogic() == null ? previous.getCriteriaLogic() : incoming.getCriteriaLogic());
        merged.setDisplayList(incoming.getDisplayList() == null ? previous.getDisplayList() : incoming.getDisplayList());
        if (StringUtils.equalsIgnoreCase(type, "advanced")) {
            merged.setSql(incoming.getSql() == null ? previous.getSql() : incoming.getSql());
            merged.setCriteriaList(List.of());
        } else {
            merged.setCriteriaList(incoming.getCriteriaList() == null ? previous.getCriteriaList() : incoming.getCriteriaList());
            merged.setSql(null);
        }
        merged.setPage(incoming.getPage() == null ? previous.getPage() : incoming.getPage());
        merged.setSize(incoming.getSize() == null ? previous.getSize() : incoming.getSize());
        merged.setSortBy(entityChanged ? incoming.getSortBy()
                : incoming.getSortBy() == null ? previous.getSortBy() : incoming.getSortBy());
        merged.setOrder(entityChanged ? incoming.getOrder()
                : incoming.getOrder() == null ? previous.getOrder() : incoming.getOrder());
        return merged;
    }

    private PersistedRetrievalRule toPersistedRule(RetrievalRequestDto request,
                                                    com.coolxer.model.retrieval.rule.RetrievalRule generated) {
        PersistedRetrievalRule persisted = new PersistedRetrievalRule();
        persisted.setSchemaVersion(2);
        persisted.setType(StringUtils.isNotBlank(generated.getWhereExpression()) ? "advanced" : "normal");
        persisted.setEntity(request.getEntity());
        persisted.setCriteriaLogic(generated.getCriteriaLogic());
        persisted.setSql(generated.getWhereExpression());
        persisted.setCriteriaList(StringUtils.isNotBlank(generated.getWhereExpression()) || generated.getRetrievalCriteria() == null
                ? List.of()
                : generated.getRetrievalCriteria().stream().map(this::toRequestCriteriaDto).toList());
        persisted.setDisplayList(toRequestDisplayDtoList(generated.getDisplayAttributes()));
        persisted.setPage(request.getPage());
        persisted.setSize(request.getSize());
        persisted.setSortBy(request.getSortBy());
        persisted.setOrder(request.getOrder());
        return persisted;
    }

    private RetrievalRequestDto readStoredConfig(RetrievalRule entity) {
        try {
            return doReadStoredConfig(entity);
        } catch (RuntimeException ex) {
            log.warn("invalid persisted retrieval rule, id={}, reason={}", entity.getId(), ex.getMessage());
            RetrievalRequestDto invalid = new RetrievalRequestDto();
            invalid.setType("invalid");
            invalid.setCriteriaList(List.of());
            invalid.setDisplayList(List.of());
            return invalid;
        }
    }

    private RetrievalRequestDto doReadStoredConfig(RetrievalRule entity) {
        Map<String, Object> raw = JacksonUtil.toMap(entity.getRuleString(), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        Object schemaVersion = raw.get("schema_version");
        if (Objects.equals(2, schemaVersion) || Objects.equals(2L, schemaVersion)
                || "2".equals(String.valueOf(schemaVersion))) {
            PersistedRetrievalRule persisted = JacksonUtil.toObject(entity.getRuleString(), PersistedRetrievalRule.class);
            return fromPersistedRule(persisted);
        }
        com.coolxer.model.retrieval.rule.RetrievalRule legacy = JacksonUtil.toObject(
                entity.getRuleString(), com.coolxer.model.retrieval.rule.RetrievalRule.class);
        return fromLegacyRule(legacy);
    }

    private RetrievalRequestDto fromPersistedRule(PersistedRetrievalRule persisted) {
        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setType(persisted.getType());
        request.setEntity(persisted.getEntity());
        request.setCriteriaList(persisted.getCriteriaList());
        request.setCriteriaLogic(persisted.getCriteriaLogic());
        request.setSql(persisted.getSql());
        request.setDisplayList(persisted.getDisplayList());
        request.setPage(persisted.getPage());
        request.setSize(persisted.getSize());
        request.setSortBy(persisted.getSortBy());
        request.setOrder(persisted.getOrder());
        return request;
    }

    private RetrievalRequestDto fromLegacyRule(com.coolxer.model.retrieval.rule.RetrievalRule legacy) {
        RetrievalRequestDto request = new RetrievalRequestDto();
        if (legacy == null) {
            request.setType("invalid");
            return request;
        }
        if (legacy.getRetrievalSql() != null) {
            request.setType("legacy_sql");
            request.setEntity(legacy.getRetrievalSql().getEntity() == null ? null : legacy.getRetrievalSql().getEntity().getName());
            request.setSql(legacy.getRetrievalSql().getSql());
        } else {
            request.setType(StringUtils.isNotBlank(legacy.getWhereExpression()) ? "advanced" : "normal");
            request.setSql(legacy.getWhereExpression());
            request.setCriteriaLogic(legacy.getCriteriaLogic());
            request.setCriteriaList(legacy.getRetrievalCriteria() == null ? List.of() : legacy.getRetrievalCriteria().stream()
                    .map(this::toRequestCriteriaDto).toList());
        }
        request.setDisplayList(toRequestDisplayDtoList(legacy.getDisplayAttributes()));
        request.setEntity(resolveLegacyEntity(legacy, request.getDisplayList()));
        if (legacy.getRetrievalPageable() != null) {
            request.setPage(legacy.getRetrievalPageable().getPage());
            request.setSize(legacy.getRetrievalPageable().getSize());
            request.setOrder(legacy.getRetrievalPageable().getOrder());
            request.setSortBy(resolveLogicalSort(request.getEntity(), legacy.getRetrievalPageable().getSortBy()));
        }
        return request;
    }

    private String resolveLegacyEntity(com.coolxer.model.retrieval.rule.RetrievalRule legacy,
                                       List<RequestDisplayDto> displayList) {
        if (CollectionUtils.isNotEmpty(displayList) && StringUtils.isNotBlank(displayList.get(0).getEntity())) {
            return displayList.get(0).getEntity();
        }
        if (CollectionUtils.isNotEmpty(legacy.getRetrievalCriteria())) {
            RetrievalCriteria criteria = legacy.getRetrievalCriteria().get(0);
            if (criteria.getEntity() != null) {
                return criteria.getEntity().getName();
            }
            if (criteria.getAttribute() != null) {
                return criteria.getAttribute().getEntity();
            }
        }
        if (legacy.getRetrievalSql() != null && legacy.getRetrievalSql().getEntity() != null) {
            return legacy.getRetrievalSql().getEntity().getName();
        }
        return null;
    }

    private String resolveLogicalSort(String entity, String storedSort) {
        if (StringUtils.isBlank(storedSort)) {
            return null;
        }
        DataAttribute logical = metaDataService.getDataAttributeByName(entity, storedSort);
        if (logical != null) {
            return logical.getName();
        }
        return metaDataService.getAllDataAttribute().stream()
                .filter(attribute -> Objects.equals(entity, attribute.getEntity()))
                .filter(attribute -> Objects.equals(storedSort, attribute.getColumnName()))
                .map(DataAttribute::getName)
                .findFirst().orElse(null);
    }

    private RequestCriteriaDto toRequestCriteriaDto(RetrievalCriteria criteria) {
        RequestCriteriaDto dto = new RequestCriteriaDto();
        dto.setAttribute(criteria == null || criteria.getAttribute() == null ? null : criteria.getAttribute().getName());
        dto.setOperator(criteria == null || criteria.getOperator() == null ? null : criteria.getOperator().getName());
        dto.setValueList(criteria == null || criteria.getValueList() == null ? List.of() : criteria.getValueList());
        return dto;
    }

    private List<RequestDisplayDto> toRequestDisplayDtoList(List<DisplayAttribute> displayAttributes) {
        if (CollectionUtils.isEmpty(displayAttributes)) {
            return List.of();
        }
        return displayAttributes.stream().map(display -> {
            RequestDisplayDto dto = new RequestDisplayDto();
            dto.setEntity(display == null || display.getEntity() == null ? null : display.getEntity().getName());
            dto.setAttributeList(display == null || display.getAttributeList() == null ? List.of() :
                    display.getAttributeList().stream().map(attribute -> attribute == null ? null : attribute.getName()).toList());
            return dto;
        }).toList();
    }

    private List<RetrievalRuleIssueVo> validateStoredConfig(RetrievalRequestDto config) {
        List<RetrievalRuleIssueVo> issues = new ArrayList<>();
        if (config == null) {
            issues.add(issue("RULE_EMPTY", "rule", null, null, "规则配置为空"));
            return issues;
        }
        if ("legacy_sql".equals(config.getType())) {
            issues.add(issue("LEGACY_SQL_DISABLED", "rule", config.getEntity(), null, "旧自由SQL规则已禁用，请重新配置"));
            return issues;
        }
        DataEntity entity = metaDataService.getDataEntityByName(config.getEntity());
        if (entity == null) {
            issues.add(issue("ENTITY_MISSING", "entity", config.getEntity(), null, "检索实体不存在"));
        }
        if (CollectionUtils.isEmpty(config.getDisplayList())) {
            issues.add(issue("DISPLAY_EMPTY", "display", config.getEntity(), null, "至少需要一个展示字段"));
        } else {
            config.getDisplayList().forEach(display -> {
                if (display == null || !Objects.equals(config.getEntity(), display.getEntity())) {
                    issues.add(issue("DISPLAY_ENTITY_MISMATCH", "display", display == null ? null : display.getEntity(), null,
                            "展示实体与检索实体不一致"));
                    return;
                }
                if (CollectionUtils.isEmpty(display.getAttributeList())) {
                    issues.add(issue("DISPLAY_EMPTY", "display", display.getEntity(), null, "至少需要一个展示字段"));
                    return;
                }
                display.getAttributeList().forEach(attribute -> {
                    if (metaDataService.getDataAttributeByName(display.getEntity(), attribute) == null) {
                        issues.add(issue("DISPLAY_FIELD_MISSING", "display", display.getEntity(), attribute,
                                "展示字段不存在: " + attribute));
                    }
                });
            });
        }
        if (StringUtils.equalsIgnoreCase(config.getType(), "advanced")) {
            try {
                WhereExpressionParser.WhereExpression expression = whereExpressionParser.parse(config.getSql());
                expression.criteriaList().forEach(criteria -> {
                    if (metaDataService.getDataAttributeByName(config.getEntity(), criteria.getAttribute()) == null) {
                        issues.add(issue("CRITERIA_FIELD_MISSING", "criteria", config.getEntity(), criteria.getAttribute(),
                                "检索字段不存在: " + criteria.getAttribute()));
                    }
                    if (metaDataService.getDataOperatorByName(criteria.getOperator()) == null) {
                        issues.add(issue("OPERATOR_MISSING", "criteria", config.getEntity(), criteria.getAttribute(),
                                "检索操作符不存在: " + criteria.getOperator()));
                    }
                });
            } catch (RuntimeException ex) {
                issues.add(issue("INVALID_EXPRESSION", "criteria", config.getEntity(), null, ex.getMessage()));
            }
        } else if (StringUtils.equalsIgnoreCase(config.getType(), "normal")) {
            for (RequestCriteriaDto criteria : config.getCriteriaList() == null ? List.<RequestCriteriaDto>of() : config.getCriteriaList()) {
                if (criteria == null || metaDataService.getDataAttributeByName(config.getEntity(), criteria.getAttribute()) == null) {
                    issues.add(issue("CRITERIA_FIELD_MISSING", "criteria", config.getEntity(),
                            criteria == null ? null : criteria.getAttribute(), "检索字段不存在"));
                }
                if (criteria != null && metaDataService.getDataOperatorByName(criteria.getOperator()) == null) {
                    issues.add(issue("OPERATOR_MISSING", "criteria", config.getEntity(), criteria.getAttribute(),
                            "检索操作符不存在: " + criteria.getOperator()));
                }
            }
        } else {
            issues.add(issue("TYPE_INVALID", "rule", config.getEntity(), null, "检索类型不正确"));
        }
        if (issues.isEmpty()) {
            try {
                generateRetrievalRule(config);
            } catch (RuntimeException ex) {
                issues.add(issue("RULE_INVALID", "rule", config.getEntity(), null, ex.getMessage()));
            }
        }
        return issues;
    }

    private RetrievalRuleIssueVo issue(String code, String scope, String entity, String attribute, String message) {
        return new RetrievalRuleIssueVo(code, scope, entity, attribute, message);
    }

    private RetrievalRuleConfigVo toConfigVo(RetrievalRequestDto config) {
        RetrievalRuleConfigVo vo = new RetrievalRuleConfigVo();
        vo.setType(config.getType());
        vo.setEntity(config.getEntity());
        vo.setCriteriaList(config.getCriteriaList() == null ? List.of() : config.getCriteriaList());
        vo.setCriteriaLogic(config.getCriteriaLogic());
        vo.setSql(config.getSql());
        vo.setDisplayList(config.getDisplayList() == null ? List.of() : config.getDisplayList());
        return vo;
    }

    private DataEntityVo toDataEntityVo(DataEntity entity) {
        DataEntityVo vo = new DataEntityVo();
        vo.setName(entity.getName());
        vo.setLabel(entity.getLabel());
        vo.setDescription(entity.getDescription());
        return vo;
    }

    private DataAttributeVo toDataAttributeVo(DataAttribute attribute) {
        DataAttributeVo vo = new DataAttributeVo();
        vo.setName(attribute.getName());
        vo.setLabel(attribute.getLabel());
        vo.setDescription(attribute.getDescription());
        vo.setRetrievalType(attribute.getRetrievalType());
        vo.setSearchType(attribute.getSearchType());
        vo.setDisplayType(attribute.getDisplayType());
        vo.setLinkTemplate(attribute.getLinkTemplate());
        vo.setAutoComplete(attribute.isAutoComplete());
        vo.setCopyable(attribute.isCopyable());
        vo.setOperatorList(attribute.getOperators() == null ? List.of() : attribute.getOperators().stream()
                .map(metaDataService::getDataOperatorByName)
                .filter(Objects::nonNull)
                .map(operator -> {
                    OperatorVo operatorVo = new OperatorVo();
                    operatorVo.setName(operator.getName());
                    operatorVo.setLabel(operator.getLabel());
                    return operatorVo;
                }).toList());
        return vo;
    }

    @Override
    public com.coolxer.model.retrieval.rule.RetrievalRule generateRetrievalRule(RetrievalRequestDto retrievalRequestDTO) {
        if (retrievalRequestDTO == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索请求不能为空");
        }
        validateRetrievalRequest(retrievalRequestDTO);
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

    private void validateRetrievalRequest(RetrievalRequestDto request) {
        if (StringUtils.isBlank(request.getEntity())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索实体不能为空");
        }
        if (metaDataService.getDataEntityByName(request.getEntity()) == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "检索实体不存在: " + request.getEntity());
        }
        String type = StringUtils.defaultIfBlank(request.getType(), StringUtils.isNotBlank(request.getSql()) ? "advanced" : "normal");
        if (!StringUtils.equalsAnyIgnoreCase(type, "normal", "advanced")) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "检索类型仅支持normal或advanced");
        }
        if (StringUtils.equalsIgnoreCase(type, "advanced") && StringUtils.isBlank(request.getSql())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "高级where表达式不能为空");
        }
        if (StringUtils.equalsIgnoreCase(type, "normal") && StringUtils.isNotBlank(request.getSql())) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "普通检索不能提交高级where表达式");
        }
        if (CollectionUtils.size(request.getCriteriaList()) > WhereExpressionParser.MAX_CONDITION_COUNT) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "检索条件不能超过" + WhereExpressionParser.MAX_CONDITION_COUNT + "个");
        }
        if (request.getCriteriaList() != null) {
            request.getCriteriaList().forEach(criteria -> {
                List<String> values = criteria == null || criteria.getValueList() == null ? List.of() : criteria.getValueList();
                if (values.size() > WhereExpressionParser.MAX_IN_VALUES) {
                    throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "单个检索条件的值不能超过" + WhereExpressionParser.MAX_IN_VALUES + "个");
                }
                values.stream().filter(Objects::nonNull).filter(value -> value.length() > WhereExpressionParser.MAX_VALUE_LENGTH)
                        .findFirst().ifPresent(value -> {
                            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(),
                                    "检索条件单值不能超过" + WhereExpressionParser.MAX_VALUE_LENGTH + "个字符");
                        });
            });
        }
        if (CollectionUtils.size(request.getDisplayList()) != 1) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "检索只支持单个展示实体");
        }
        RequestDisplayDto display = request.getDisplayList().get(0);
        if (display == null || !Objects.equals(request.getEntity(), display.getEntity())) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "展示实体必须与检索实体一致");
        }
        int displayCount = CollectionUtils.size(display.getAttributeList());
        if (displayCount < 1 || displayCount > 100) {
            throw new ApiException(ResultCodeEnum.DISPLAY_LIMIT_ERROR.getCode(), "展示字段数量必须为1到100个");
        }
        Set<String> distinctDisplay = new LinkedHashSet<>(display.getAttributeList());
        if (distinctDisplay.size() != displayCount || distinctDisplay.contains(null)) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "展示字段不能重复或为空");
        }
        if (request.getPage() != null && request.getPage() < 1) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "页码必须大于等于1");
        }
        if (request.getSize() != null && (request.getSize() < 1 || request.getSize() > 100)) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "页大小必须为1到100");
        }
        if (StringUtils.isNotBlank(request.getOrder()) && !StringUtils.equalsAnyIgnoreCase(request.getOrder(), "asc", "desc")) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "排序方向仅支持asc或desc");
        }
        if (StringUtils.isNotBlank(request.getSortBy())
                && metaDataService.getDataAttributeByName(request.getEntity(), request.getSortBy()) == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "排序字段不存在: " + request.getSortBy());
        }
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
        if (StringUtils.isNotBlank(retrievalRequestDto.getSortBy()) && dataAttribute == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "排序字段不存在: " + retrievalRequestDto.getSortBy());
        }
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
