package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.model.retrieval.query.ColumnCriteria;
import com.coolxer.model.retrieval.query.ColumnCriteriaExpression;
import com.coolxer.model.retrieval.query.DataQuery;
import com.coolxer.model.retrieval.query.DataQueryContext;
import com.coolxer.model.retrieval.query.DisplayColumn;
import com.coolxer.model.retrieval.rule.RetrievalCriteria;
import com.coolxer.model.retrieval.rule.RetrievalCriteriaExpression;
import com.coolxer.model.retrieval.rule.RetrievalPageable;
import com.coolxer.model.retrieval.rule.RetrievalRule;
import com.coolxer.service.retrieval.DataQueryService;
import com.coolxer.service.retrieval.QueryEngine;
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
public class DataQueryServiceImpl implements DataQueryService {

    @Autowired
    QueryEngine queryEngine;

    @Override
    public DataQueryContext query(RetrievalRule retrievalRule) {
        long startedAt = System.nanoTime();
        DataQueryContext context = generateQueryContext(retrievalRule);
        executeQuery(context);
        String entity = retrievalRule.getDisplayAttributes().get(0).getEntity().getName();
        int conditionCount = CollectionUtils.size(retrievalRule.getRetrievalCriteria());
        int fieldCount = CollectionUtils.size(retrievalRule.getDisplayAttributes().get(0).getAttributeList());
        log.info("retrieval query completed, context_id={}, entity={}, conditions={}, fields={}, duration_ms={}",
                context.getContextId(), entity, conditionCount, fieldCount,
                (System.nanoTime() - startedAt) / 1_000_000);
        return context;
    }

    private DataQueryContext generateQueryContext(RetrievalRule retrievalRule) {
        if (retrievalRule == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索规则不能为空");
        }
        DataQueryContext dataQueryContext = new DataQueryContext();
        dataQueryContext.setContextId(UUID.randomUUID().toString());
        List<DataQuery> dataQueryList;
        if (Objects.nonNull(retrievalRule.getRetrievalSql())) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "自由SQL检索规则已禁用，请使用受限where表达式");

        } else {
            dataQueryList = generateDataQueryList(retrievalRule);
        }
        if (CollectionUtils.isEmpty(dataQueryList)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索规则缺少可执行查询");
        }
        dataQueryContext.setQueryChain(dataQueryList);
        RetrievalPageable pageable = retrievalRule.getRetrievalPageable();
        if (Objects.nonNull(pageable)) {
            dataQueryContext.setPageable(pageable);
        }
        return dataQueryContext;
    }

    private List<DataQuery> generateDataQueryList(RetrievalRule retrievalRule) {
        if (CollectionUtils.isEmpty(retrievalRule.getDisplayAttributes())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "展示字段不能为空");
        }
        Map<String, List<DisplayColumn>> displayColumnNameMap = new HashMap<>();
        retrievalRule.getDisplayAttributes().forEach(table -> {
            if (table == null || table.getEntity() == null || CollectionUtils.isEmpty(table.getAttributeList())) {
                throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "展示字段不能为空");
            }
            List<DisplayColumn> displayColumnList = buildDisplayColumns(table.getAttributeList());
            displayColumnNameMap.put(table.getEntity().getTableName(), displayColumnList);
        });
        if (Objects.nonNull(retrievalRule.getCriteriaExpression())) {
            return generateExpressionDataQueryList(retrievalRule, displayColumnNameMap);
        }
        List<RetrievalCriteria> criteriaList = retrievalRule.getRetrievalCriteria() == null
                ? Collections.emptyList() : retrievalRule.getRetrievalCriteria();
        List<DataQuery> dataQueryList = criteriaList.stream()
                .map(this::toColumnCriteria)
                .collect(Collectors.groupingBy(ColumnCriteria::getTableName))
                .entrySet().stream()
                .map(entry -> {
                    DataQuery dataQuery = new DataQuery();
                    dataQuery.setTableName(entry.getKey());
                    dataQuery.setColumnCriteria(entry.getValue());
                    dataQuery.setCriteriaLogic(retrievalRule.getCriteriaLogic());
                    dataQuery.setDisplayColumnList(displayColumnNameMap.get(dataQuery.getTableName()));
                    return dataQuery;
                })
                .collect(Collectors.toList());
        Collection<String> extraDisplayTable = CollectionUtils.subtract(displayColumnNameMap.keySet(), dataQueryList.stream().map(DataQuery::getTableName).toList());
        if (CollectionUtils.isNotEmpty(extraDisplayTable)) {
            extraDisplayTable.forEach(table -> {
                DataQuery dataQuery = new DataQuery();
                dataQuery.setDisplayColumnList(displayColumnNameMap.get(table));
                dataQuery.setTableName(table);
                dataQuery.setCriteriaLogic(retrievalRule.getCriteriaLogic());
                dataQueryList.add(dataQuery);
            });
        }
        return dataQueryList;
    }

    private List<DisplayColumn> buildDisplayColumns(List<DataAttribute> attributes) {
        List<DisplayColumn> columns = attributes.stream()
                .map(attribute -> new DisplayColumn().fromDisplayColumn(attribute))
                .collect(Collectors.toCollection(ArrayList::new));
        boolean recordIdSelected = attributes.stream()
                .anyMatch(attribute -> MetaDataConstants.RECORD_ID_ATTRIBUTE.equals(attribute.getName()));
        boolean recordIdRequiredByLink = attributes.stream()
                .map(DataAttribute::getLinkTemplate)
                .filter(StringUtils::isNotBlank)
                .anyMatch(template -> template.contains("{" + MetaDataConstants.RECORD_ID_ATTRIBUTE + "}"));
        if (recordIdRequiredByLink && !recordIdSelected) {
            DisplayColumn recordIdColumn = new DisplayColumn();
            recordIdColumn.setColumnName(MetaDataConstants.RECORD_ID_COLUMN);
            recordIdColumn.setColumnType(MetaDataConstants.RECORD_ID_COLUMN_TYPE);
            recordIdColumn.setDisplayName(MetaDataConstants.RECORD_ID_ATTRIBUTE);
            columns.add(recordIdColumn);
        }
        return columns;
    }

    private List<DataQuery> generateExpressionDataQueryList(RetrievalRule retrievalRule, Map<String, List<DisplayColumn>> displayColumnNameMap) {
        Set<String> criteriaTables = collectExpressionTables(retrievalRule.getCriteriaExpression());
        if (criteriaTables.size() != 1) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "暂不支持跨实体检索");
        }
        String tableName = criteriaTables.iterator().next();
        Set<String> displayTables = displayColumnNameMap.keySet();
        if (displayTables.size() > 1 || (!displayTables.isEmpty() && !displayTables.contains(tableName))) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "暂不支持跨实体检索");
        }
        DataQuery dataQuery = new DataQuery();
        dataQuery.setTableName(tableName);
        dataQuery.setCriteriaExpression(toColumnCriteriaExpression(retrievalRule.getCriteriaExpression()));
        dataQuery.setColumnCriteria(retrievalRule.getRetrievalCriteria().stream().map(this::toColumnCriteria).toList());
        dataQuery.setDisplayColumnList(displayColumnNameMap.get(tableName));
        return List.of(dataQuery);
    }

    private Set<String> collectExpressionTables(RetrievalCriteriaExpression expression) {
        if ("condition".equals(expression.getType())) {
            return Set.of(expression.getCriteria().getEntity().getTableName());
        }
        if (CollectionUtils.isEmpty(expression.getChildren())) {
            return Collections.emptySet();
        }
        return expression.getChildren().stream()
                .flatMap(child -> collectExpressionTables(child).stream())
                .collect(Collectors.toSet());
    }

    private ColumnCriteriaExpression toColumnCriteriaExpression(RetrievalCriteriaExpression expression) {
        ColumnCriteriaExpression columnExpression = new ColumnCriteriaExpression();
        columnExpression.setType(expression.getType());
        columnExpression.setLogic(expression.getLogic());
        if ("condition".equals(expression.getType())) {
            columnExpression.setCriteria(toColumnCriteria(expression.getCriteria()));
        } else {
            columnExpression.setChildren(expression.getChildren().stream()
                    .map(this::toColumnCriteriaExpression)
                    .toList());
        }
        return columnExpression;
    }

    private ColumnCriteria toColumnCriteria(RetrievalCriteria retrievalCriteria) {
        ColumnCriteria columnCriteria = new ColumnCriteria();
        columnCriteria.setTableName(retrievalCriteria.getEntity().getTableName());
        columnCriteria.setColumnName(retrievalCriteria.getAttribute().getColumnName());
        columnCriteria.setColumnType(retrievalCriteria.getAttribute().getColumnType());
        columnCriteria.setOperatorName(retrievalCriteria.getOperator().getName());
        columnCriteria.setValueList(retrievalCriteria.getValueList());
        columnCriteria.setRetrievalType(retrievalCriteria.getAttribute().getRetrievalType());
        return columnCriteria;
    }

    private void executeQuery(DataQueryContext context) {
        if (CollectionUtils.size(context.getQueryChain()) > 1) {
            chainQuery(context);
        } else {
            singleQuery(context);
        }
//    Integer resultLimit = context.getResultLimit();
//    List<Map<String, Object>> resultList = context.getResultList();
//    while (CollectionUtils.size(resultList) < resultLimit) {
//      DataQuery previousQuery = null;
//      for (DataQuery currentQuery : context.getQueryChain()) {
//        if (needQuery(currentQuery)) {
//
//        } else {
//          updateCursor(currentQuery);
//        }
//      }
//    }
//    return buildResult(context);
        // comit


//    Map<String, Object> record1 = new HashMap<>();
//    record1.put("model", "xiaomi");
//    record1.put("mac", "abcd");
//    Map<String, Object> record2 = new HashMap<>();
//    record2.put("model", "huawei");
//    record2.put("mac", "bdcd");
//    List<Map<String, Object>> resultList = new ArrayList<>();
//    resultList.add(record1);
//    resultList.add(record2);
//    context.setResultList(resultList);
    }

    private void chainQuery(DataQueryContext context) {
        throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "暂不支持跨实体检索");
    }

    private void singleQuery(DataQueryContext context) {
        if (context == null || CollectionUtils.isEmpty(context.getQueryChain())) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索规则缺少可执行查询");
        }
        DataQuery dataQuery = context.getQueryChain().get(0);
        RetrievalPageable pageable = context.getPageable();
        Map<String, Object> resultMap = queryEngine.queryWithRetrieval(dataQuery, pageable);

        context.setResultList((List<Map<String, Object>>) resultMap.get("data"));
        context.setTotal((BigDecimal) resultMap.get("total"));
    }

    private boolean needQuery(DataQuery query) {
        return true;
    }

    private void updateCursor(DataQuery query) {

    }

    private Map<String, Object> buildResult(DataQueryContext context) {
        return null;
    }
}
