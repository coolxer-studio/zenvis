package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.query.ColumnCriteria;
import com.coolxer.model.retrieval.query.ColumnCriteriaExpression;
import com.coolxer.model.retrieval.query.DisplayColumn;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.rule.RetrievalPageable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyString;

class QueryEngineImplTest {

    @Test
    void readQueriesDoNotOpenUnsupportedClickHouseTransactions() throws NoSuchMethodException {
        assertThat(QueryEngineImpl.class.getMethod(
                "countByTimeRange",
                String.class, String.class, String.class, String.class,
                Date.class, Date.class, boolean.class).getAnnotation(Transactional.class))
                .isNull();

        Transactional writeTransaction = QueryEngineImpl.class.getMethod(
                "save", String.class, List.class, List.class).getAnnotation(Transactional.class);
        assertThat(writeTransaction).isNotNull();
        assertThat(writeTransaction.transactionManager()).isEqualTo("clickHouseTransactionManager");
    }

    @Test
    void findByIdUsesValidatedPlatformKeyColumn() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(
                new Object[]{"8e388586-24b2-4d4b-aecc-a33151326f4d"}));
        ReflectionTestUtils.setField(queryEngine, "entityManager", entityManager);
        DataAttribute recordId = new DataAttribute();
        recordId.setName("zenvis_id");
        recordId.setColumnName("zenvis_id");
        recordId.setColumnType("Nullable(UUID)");

        Map<String, Object> result = queryEngine.findById(
                "zenvis.asset", "zenvis_id", "8e388586-24b2-4d4b-aecc-a33151326f4d",
                List.of(recordId));

        assertThat(result).containsEntry("zenvis_id", "8e388586-24b2-4d4b-aecc-a33151326f4d");
        verify(entityManager).createNativeQuery(
                "select zenvis_id from zenvis.asset where zenvis_id = '8e388586-24b2-4d4b-aecc-a33151326f4d'");
        assertThatThrownBy(() -> queryEngine.findById(
                "zenvis.asset", "zenvis_id or 1=1", "value", List.of(recordId)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("字段名不合法");
    }

    @Test
    void buildCriteriaSqlEscapesStringValues() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();

        String criteriaSql = ReflectionTestUtils.invokeMethod(
                queryEngine,
                "buildCriteriaSql",
                criteria("src_ip", "String", "equal", "10.0.0.1' OR 1=1 --")
        );

        assertThat(criteriaSql).isEqualTo("src_ip = '10.0.0.1'' OR 1=1 --'");
    }

    @Test
    void buildCriteriaSqlValidatesUuidValuesBeforeExecutingClickHouseQuery() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();
        String uuid = "8e388586-24b2-4d4b-aecc-a33151326f4d";

        String criteriaSql = ReflectionTestUtils.invokeMethod(
                queryEngine,
                "buildCriteriaSql",
                criteria("zenvis_id", "Nullable(UUID)", "equal", uuid)
        );

        assertThat(criteriaSql).isEqualTo("zenvis_id = '" + uuid + "'");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                queryEngine,
                "buildCriteriaSql",
                criteria("zenvis_id", "Nullable(UUID)", "equal", "111")
        ))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("UUID条件值必须为标准UUID格式");
    }

    @Test
    void buildCriteriaSqlSupportsValuelessNullOperators() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();

        String stringNullSql = ReflectionTestUtils.invokeMethod(
                queryEngine,
                "buildCriteriaSql",
                criteria("attack_type_name", "String", "isnull", List.of())
        );
        String numberNotNullSql = ReflectionTestUtils.invokeMethod(
                queryEngine,
                "buildCriteriaSql",
                criteria("linkage_status", "Int32", "isnotnull", List.of())
        );
        String arrayNotNullSql = ReflectionTestUtils.invokeMethod(
                queryEngine,
                "buildCriteriaSql",
                criteria("tags", "Array(String)", "isnotnull", List.of())
        );

        assertThat(stringNullSql).isEqualTo("(attack_type_name is null or length(attack_type_name) = 0)");
        assertThat(numberNotNullSql).isEqualTo("linkage_status is not null");
        assertThat(arrayNotNullSql).isEqualTo("(tags is not null and length(tags) > 0)");
    }

    @Test
    void buildPageKeepsSafeSortAndRejectsUnsafeSortIdentifier() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();

        String page = ReflectionTestUtils.invokeMethod(
                queryEngine,
                "buildPage",
                new RetrievalPageable(1, 10, "server_time", "asc")
        );
        assertThat(page).isEqualTo(" order by server_time asc limit 0,10");
        assertThat((String) ReflectionTestUtils.invokeMethod(
                queryEngine,
                "buildPage",
                new RetrievalPageable(1, 100, "server_time", null)
        )).isEqualTo(" order by server_time desc limit 0,100");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                queryEngine,
                "buildPage",
                new RetrievalPageable(1, 10, "server_time desc", "asc")
        ))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("排序字段不合法");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                queryEngine, "buildPage", new RetrievalPageable(0, 10, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("分页参数");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                queryEngine, "buildPage", new RetrievalPageable(1, 101, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("分页参数");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                queryEngine, "buildPage", new RetrievalPageable(1, 10, "server_time", "sideways")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("排序方向");
    }

    @Test
    void dateConversionUsesConfiguredBusinessTimeZone() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();
        ReflectionTestUtils.setField(queryEngine, "retrievalTimeZone", "Asia/Shanghai");

        String value = ReflectionTestUtils.invokeMethod(
                queryEngine, "convertValueList", "1970-01-01 08:00:00", "date", "Int64");

        assertThat(value).isEqualTo("0");
    }

    @Test
    void dateRetrievalTypeUsesDateTime64LiteralForTemporalColumn() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();
        ReflectionTestUtils.setField(queryEngine, "retrievalTimeZone", "Asia/Shanghai");
        ColumnCriteria criteria = criteria(
                "zenvis_insert_time", "DateTime64(3)", "greatequalthan", "2026-07-15 09:00:00");
        criteria.setRetrievalType("date");

        String criteriaSql = ReflectionTestUtils.invokeMethod(queryEngine, "buildCriteriaSql", criteria);

        assertThat(criteriaSql).isEqualTo(
                "zenvis_insert_time >= toDateTime64('2026-07-15 09:00:00', 3, 'Asia/Shanghai')");
    }

    @Test
    void dateRetrievalTypeConvertsEpochMillisecondsForTemporalBetweenCriteria() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();
        ReflectionTestUtils.setField(queryEngine, "retrievalTimeZone", "Asia/Shanghai");
        ColumnCriteria criteria = criteria(
                "found_time", "DateTime", "between",
                List.of("1784821799000", "1784822999000"));
        criteria.setRetrievalType("date");

        String criteriaSql = ReflectionTestUtils.invokeMethod(queryEngine, "buildCriteriaSql", criteria);

        assertThat(criteriaSql).isEqualTo(
                "found_time between "
                        + "toDateTime('2026-07-23 23:49:59', 'Asia/Shanghai') and "
                        + "toDateTime('2026-07-24 00:09:59', 'Asia/Shanghai')");
    }

    @Test
    void trendTimeExpressionSupportsSecondsMillisecondsAndDateTime() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();
        ReflectionTestUtils.setField(queryEngine, "retrievalTimeZone", "Asia/Shanghai");

        String seconds = ReflectionTestUtils.invokeMethod(
                queryEngine, "trendTimeExpression", "event_time", "Int64", "seconds");
        String milliseconds = ReflectionTestUtils.invokeMethod(
                queryEngine, "trendTimeExpression", "event_time", "Nullable(UInt64)", "milliseconds");
        String dateTime = ReflectionTestUtils.invokeMethod(
                queryEngine, "trendTimeExpression", "event_time", "DateTime64(3)", null);

        assertThat(seconds).isEqualTo("toDateTime(event_time, 'Asia/Shanghai')");
        assertThat(milliseconds).isEqualTo("toDateTime(event_time / 1000, 'Asia/Shanghai')");
        assertThat(dateTime).isEqualTo("toTimeZone(event_time, 'Asia/Shanghai')");
    }

    @Test
    void countByTimeRangeReturnsBucketCounts() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{"09:00", 7L}));
        ReflectionTestUtils.setField(queryEngine, "entityManager", entityManager);
        ReflectionTestUtils.setField(queryEngine, "retrievalTimeZone", "Asia/Shanghai");
        Date start = new Date(1_000L);
        Date end = new Date(2_000L);

        Map<String, Long> result = queryEngine.countByTimeRange(
                "asset_table", "event_time", "Int64", "seconds", start, end, true);

        assertThat(result).containsEntry("09:00", 7L);
        verify(query).setParameter("startTime", "1970-01-01 08:00:01.000");
        verify(query).setParameter("endTime", "1970-01-01 08:00:02.000");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).contains(
                "toDateTime64(:startTime, 3, 'Asia/Shanghai')",
                "toDateTime64(:endTime, 3, 'Asia/Shanghai')");
    }

    @Test
    void countAnyOfUsesOneOrPredicateAndBindsTheExactValue() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(BigDecimal.valueOf(4)));
        ReflectionTestUtils.setField(queryEngine, "entityManager", entityManager);

        BigDecimal result = queryEngine.countAnyOf(
                "zenvis.traffic",
                List.of("src_ip", "dest_ip", "src_ip"),
                "10.0.0.1' OR 1=1 --");

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(4));
        verify(entityManager).createNativeQuery(
                "select count(*) from zenvis.traffic where src_ip = :value or dest_ip = :value");
        verify(query).setParameter("value", "10.0.0.1' OR 1=1 --");
    }

    @Test
    void countAnyOfRejectsUnsafeTableAndFieldIdentifiers() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();

        assertThatThrownBy(() -> queryEngine.countAnyOf(
                "traffic; drop table traffic", List.of("src_ip"), "192.0.2.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("表名不合法");
        assertThatThrownBy(() -> queryEngine.countAnyOf(
                "traffic", List.of("src_ip or 1=1"), "192.0.2.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("字段名不合法");
    }

    @Test
    void countAnyOfRejectsMissingFieldsAndBlankValue() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();

        assertThatThrownBy(() -> queryEngine.countAnyOf("traffic", List.of(), "192.0.2.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("统计字段不能为空");
        assertThatThrownBy(() -> queryEngine.countAnyOf("traffic", List.of("src_ip"), " "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("统计值不能为空");
    }

    @Test
    void displayColumnAlwaysUsesLogicalNameAsResponseAlias() {
        DataAttribute attribute = new DataAttribute();
        attribute.setName("device_name");
        attribute.setColumnName("dev_name");
        attribute.setDisplayName("设备名称");

        DisplayColumn column = new DisplayColumn().fromDisplayColumn(attribute);

        assertThat(column.getDisplayName()).isEqualTo("device_name");
        assertThat(column.getColumnName()).isEqualTo("dev_name");
    }

    @Test
    void buildCriteriaExpressionSqlKeepsParenthesizedLogic() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();

        ColumnCriteriaExpression root = group("and",
                condition(criteria("module_type_name", "String", "equal", "网站攻击")),
                group("or",
                        condition(criteria("attack_type_name", "String", "equal", "信息泄露")),
                        condition(criteria("attack_type_name", "String", "equal", "SQL注入"))
                )
        );

        String criteriaSql = ReflectionTestUtils.invokeMethod(
                queryEngine,
                "buildCriteriaExpressionSql",
                root
        );

        assertThat(criteriaSql).isEqualTo("(module_type_name = '网站攻击' and (attack_type_name = '信息泄露' or attack_type_name = 'SQL注入'))");
    }

    @Test
    void singleNullJsonColumnKeepsLogicalKeyAndNullValue() {
        QueryEngineImpl queryEngine = new QueryEngineImpl();
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery("select payload from asset")).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(null));
        ReflectionTestUtils.setField(queryEngine, "entityManager", entityManager);
        DisplayColumn column = new DisplayColumn();
        column.setColumnName("payload_json");
        column.setDisplayName("payload");
        column.setDisplayType("json");

        List<Map<String, Object>> result = ReflectionTestUtils.invokeMethod(
                queryEngine, "queryResultList", "select payload from asset", List.of(column));

        assertThat(result).containsExactly(Collections.singletonMap("payload", null));
    }

    private ColumnCriteria criteria(String column, String columnType, String operator, String value) {
        return criteria(column, columnType, operator, List.of(value));
    }

    private ColumnCriteria criteria(String column, String columnType, String operator, List<String> valueList) {
        ColumnCriteria criteria = new ColumnCriteria();
        criteria.setColumnName(column);
        criteria.setColumnType(columnType);
        criteria.setOperatorName(operator);
        criteria.setValueList(valueList);
        return criteria;
    }

    private ColumnCriteriaExpression condition(ColumnCriteria criteria) {
        ColumnCriteriaExpression expression = new ColumnCriteriaExpression();
        expression.setType("condition");
        expression.setCriteria(criteria);
        return expression;
    }

    private ColumnCriteriaExpression group(String logic, ColumnCriteriaExpression... children) {
        ColumnCriteriaExpression expression = new ColumnCriteriaExpression();
        expression.setType("group");
        expression.setLogic(logic);
        expression.setChildren(List.of(children));
        return expression;
    }

}
