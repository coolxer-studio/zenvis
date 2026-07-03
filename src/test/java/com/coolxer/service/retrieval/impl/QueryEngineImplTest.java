package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.query.ColumnCriteria;
import com.coolxer.model.retrieval.query.ColumnCriteriaExpression;
import com.coolxer.model.retrieval.rule.RetrievalPageable;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryEngineImplTest {

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

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                queryEngine,
                "buildPage",
                new RetrievalPageable(1, 10, "server_time desc", "asc")
        ))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("排序字段不合法");
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
