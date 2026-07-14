package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.coolxer.model.retrieval.dto.RequestDisplayDto;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.DataOperator;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.model.retrieval.rule.RetrievalRule;
import com.coolxer.service.retrieval.MetaDataService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalRuleServiceImplTest {

    @Test
    void generateRetrievalRuleParsesAdvancedWhereExpressionWithLogic() {
        RetrievalRuleServiceImpl retrievalRuleService = service();

        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setEntity("asset");
        request.setSql("ip = '10.0.0.1' or ip in ('10.0.0.2','10.0.0.3')");
        request.setDisplayList(List.of(display("asset", "ip")));

        RetrievalRule rule = retrievalRuleService.generateRetrievalRule(request);

        assertThat(rule.getCriteriaLogic()).isEqualTo("expression");
        assertThat(rule.getCriteriaExpression()).isNotNull();
        assertThat(rule.getRetrievalSql()).isNull();
        assertThat(rule.getRetrievalCriteria()).hasSize(2);
        assertThat(rule.getRetrievalCriteria().get(0).getValueList()).containsExactly("10.0.0.1");
        assertThat(rule.getRetrievalCriteria().get(1).getOperator().getName()).isEqualTo("in");
        assertThat(rule.getRetrievalCriteria().get(1).getValueList()).containsExactly("10.0.0.2", "10.0.0.3");
    }

    @Test
    void generateRetrievalRuleRejectsUnsafeWhereExpression() {
        RetrievalRuleServiceImpl retrievalRuleService = service();
        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setEntity("asset");
        request.setSql("ip = '10.0.0.1' or 1=1");
        request.setDisplayList(List.of(display("asset", "ip")));

        assertThatThrownBy(() -> retrievalRuleService.generateRetrievalRule(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("where");
    }

    @Test
    void generateRetrievalRuleAcceptsWherePrefixAndNormalizesLikeValue() {
        RetrievalRuleServiceImpl retrievalRuleService = service();

        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setEntity("asset");
        request.setSql("where ip like '%10.0%'");
        request.setDisplayList(List.of(display("asset", "ip")));

        RetrievalRule rule = retrievalRuleService.generateRetrievalRule(request);

        assertThat(rule.getCriteriaLogic()).isEqualTo("expression");
        assertThat(rule.getRetrievalCriteria()).hasSize(1);
        assertThat(rule.getRetrievalCriteria().get(0).getOperator().getName()).isEqualTo("match");
        assertThat(rule.getRetrievalCriteria().get(0).getValueList()).containsExactly("10.0");
    }

    @Test
    void generateRetrievalRuleSupportsParenthesesAndMixedLogicWithoutAttributeOperatorLimit() {
        RetrievalRuleServiceImpl retrievalRuleService = service();

        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setEntity("asset");
        request.setSql("module_type_name='网站攻击' and (attack_type_name=信息泄露 or attack_type_name=SQL注入)");
        request.setDisplayList(List.of(display("asset", "module_type_name")));

        RetrievalRule rule = retrievalRuleService.generateRetrievalRule(request);

        assertThat(rule.getCriteriaLogic()).isEqualTo("expression");
        assertThat(rule.getWhereExpression()).isEqualTo("module_type_name='网站攻击' and (attack_type_name=信息泄露 or attack_type_name=SQL注入)");
        assertThat(rule.getCriteriaExpression()).isNotNull();
        assertThat(rule.getRetrievalCriteria()).hasSize(3);
        assertThat(rule.getRetrievalCriteria().get(0).getAttribute().getName()).isEqualTo("module_type_name");
        assertThat(rule.getRetrievalCriteria().get(0).getValueList()).containsExactly("网站攻击");
        assertThat(rule.getRetrievalCriteria().get(1).getAttribute().getName()).isEqualTo("attack_type_name");
        assertThat(rule.getRetrievalCriteria().get(1).getValueList()).containsExactly("信息泄露");
        assertThat(rule.getRetrievalCriteria().get(2).getValueList()).containsExactly("SQL注入");
    }

    @Test
    void generateRetrievalRuleStripsSmartQuotesInAdvancedWhereExpression() {
        RetrievalRuleServiceImpl retrievalRuleService = service();

        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setEntity("asset");
        request.setSql("module_type_name=‘网站攻击’");
        request.setDisplayList(List.of(display("asset", "module_type_name")));

        RetrievalRule rule = retrievalRuleService.generateRetrievalRule(request);

        assertThat(rule.getCriteriaLogic()).isEqualTo("expression");
        assertThat(rule.getRetrievalCriteria()).hasSize(1);
        assertThat(rule.getRetrievalCriteria().get(0).getAttribute().getName()).isEqualTo("module_type_name");
        assertThat(rule.getRetrievalCriteria().get(0).getValueList()).containsExactly("网站攻击");
    }

    @Test
    void generateRetrievalRuleSupportsAdvancedNullOperators() {
        RetrievalRuleServiceImpl retrievalRuleService = service();

        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setEntity("asset");
        request.setSql("ip is not null");
        request.setDisplayList(List.of(display("asset", "ip")));

        RetrievalRule rule = retrievalRuleService.generateRetrievalRule(request);

        assertThat(rule.getRetrievalCriteria()).hasSize(1);
        assertThat(rule.getRetrievalCriteria().get(0).getOperator().getName()).isEqualTo("isnotnull");
        assertThat(rule.getRetrievalCriteria().get(0).getValueList()).isEmpty();
    }

    @Test
    void compactAdvancedRuleForPersistenceDropsExpandedExpressionAndCanHydrate() {
        RetrievalRuleServiceImpl retrievalRuleService = service();

        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setEntity("asset");
        request.setSql("module_type_name='网站攻击' and (attack_type_name=信息泄露 or attack_type_name=SQL注入)");
        request.setDisplayList(List.of(display("asset", "module_type_name")));

        RetrievalRule rule = retrievalRuleService.generateRetrievalRule(request);
        RetrievalRule compactRule = ReflectionTestUtils.invokeMethod(retrievalRuleService, "compactRuleForPersistence", rule);

        assertThat(compactRule.getCriteriaLogic()).isEqualTo("expression");
        assertThat(compactRule.getWhereExpression()).isEqualTo(rule.getWhereExpression());
        assertThat(compactRule.getCriteriaExpression()).isNull();
        assertThat(compactRule.getRetrievalCriteria()).isNull();
        assertThat(compactRule.getDisplayAttributes().get(0).getEntity().getTableName()).isNull();

        RetrievalRule hydratedRule = ReflectionTestUtils.invokeMethod(retrievalRuleService, "hydrateRule", compactRule);

        assertThat(hydratedRule.getCriteriaExpression()).isNotNull();
        assertThat(hydratedRule.getRetrievalCriteria()).hasSize(3);
        assertThat(hydratedRule.getDisplayAttributes().get(0).getEntity().getTableName()).isEqualTo("asset_table");
    }

    @Test
    void compactNormalRuleForPersistenceCanHydrateMetadata() {
        RetrievalRuleServiceImpl retrievalRuleService = service();

        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setEntity("asset");
        RequestCriteriaDto condition = new RequestCriteriaDto();
        condition.setAttribute("ip");
        condition.setOperator("equal");
        condition.setValueList(List.of("10.0.0.1"));
        request.setCriteriaList(List.of(condition));
        request.setDisplayList(List.of(display("asset", "ip")));

        RetrievalRule rule = retrievalRuleService.generateRetrievalRule(request);
        RetrievalRule compactRule = ReflectionTestUtils.invokeMethod(retrievalRuleService, "compactRuleForPersistence", rule);

        assertThat(compactRule.getRetrievalCriteria().get(0).getEntity().getTableName()).isNull();
        assertThat(compactRule.getRetrievalCriteria().get(0).getAttribute().getColumnName()).isNull();

        RetrievalRule hydratedRule = ReflectionTestUtils.invokeMethod(retrievalRuleService, "hydrateRule", compactRule);

        assertThat(hydratedRule.getRetrievalCriteria()).hasSize(1);
        assertThat(hydratedRule.getRetrievalCriteria().get(0).getEntity().getTableName()).isEqualTo("asset_table");
        assertThat(hydratedRule.getRetrievalCriteria().get(0).getAttribute().getColumnName()).isEqualTo("src_ip");
    }

    @Test
    void generateRetrievalRuleKeepsNormalCriteriaOrLogic() {
        RetrievalRuleServiceImpl retrievalRuleService = service();

        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setEntity("asset");
        request.setCriteriaLogic("or");
        RequestCriteriaDto ipCondition = new RequestCriteriaDto();
        ipCondition.setAttribute("ip");
        ipCondition.setOperator("equal");
        ipCondition.setValueList(List.of("10.0.0.1"));
        RequestCriteriaDto moduleCondition = new RequestCriteriaDto();
        moduleCondition.setAttribute("module_type_name");
        moduleCondition.setOperator("match");
        moduleCondition.setValueList(List.of("网站攻击"));
        request.setCriteriaList(List.of(ipCondition, moduleCondition));
        request.setDisplayList(List.of(display("asset", "ip")));

        RetrievalRule rule = retrievalRuleService.generateRetrievalRule(request);

        assertThat(rule.getCriteriaLogic()).isEqualTo("or");
        assertThat(rule.getRetrievalCriteria()).hasSize(2);
    }

    @Test
    void generateRetrievalRuleKeepsNormalNullOperatorWithoutValue() {
        RetrievalRuleServiceImpl retrievalRuleService = service();

        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setEntity("asset");
        RequestCriteriaDto condition = new RequestCriteriaDto();
        condition.setAttribute("ip");
        condition.setOperator("isnull");
        condition.setValueList(List.of());
        request.setCriteriaList(List.of(condition));
        request.setDisplayList(List.of(display("asset", "ip")));

        RetrievalRule rule = retrievalRuleService.generateRetrievalRule(request);

        assertThat(rule.getRetrievalCriteria()).hasSize(1);
        assertThat(rule.getRetrievalCriteria().get(0).getOperator().getName()).isEqualTo("isnull");
        assertThat(rule.getRetrievalCriteria().get(0).getValueList()).isEmpty();
    }

    @Test
    void generateRetrievalRuleRejectsAdvancedRequestWithoutWhereExpression() {
        RetrievalRuleServiceImpl retrievalRuleService = service();

        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setType("advanced");
        request.setEntity("asset");
        request.setDisplayList(List.of(display("asset", "ip")));

        assertThatThrownBy(() -> retrievalRuleService.generateRetrievalRule(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("where表达式不能为空");
    }

    @Test
    void generateRetrievalRuleRejectsUnsupportedOperator() {
        RetrievalRuleServiceImpl retrievalRuleService = service();

        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setEntity("asset");
        RequestCriteriaDto condition = new RequestCriteriaDto();
        condition.setAttribute("ip");
        condition.setOperator("between");
        condition.setValueList(List.of("1", "2"));
        request.setCriteriaList(List.of(condition));
        request.setDisplayList(List.of(display("asset", "ip")));

        assertThatThrownBy(() -> retrievalRuleService.generateRetrievalRule(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不支持");
    }

    private DataEntity entity() {
        DataEntity entity = new DataEntity();
        entity.setName("asset");
        entity.setTableName("asset_table");
        return entity;
    }

    private DataAttribute attribute(String name, String columnName, String columnType, List<String> operators) {
        DataAttribute attribute = new DataAttribute();
        attribute.setEntity("asset");
        attribute.setName(name);
        attribute.setColumnName(columnName);
        attribute.setColumnType(columnType);
        attribute.setOperators(operators);
        return attribute;
    }

    private DataOperator operator(String name) {
        DataOperator operator = new DataOperator();
        operator.setName(name);
        return operator;
    }

    private RequestDisplayDto display(String entity, String attribute) {
        RequestDisplayDto display = new RequestDisplayDto();
        display.setEntity(entity);
        display.setAttributeList(List.of(attribute));
        return display;
    }

    private RetrievalRuleServiceImpl service() {
        RetrievalRuleServiceImpl service = new RetrievalRuleServiceImpl();
        ReflectionTestUtils.setField(service, "metaDataService", new FakeMetaDataService());
        return service;
    }

    private class FakeMetaDataService implements MetaDataService {

        @Override
        public MetaData loadMetaData() {
            return null;
        }

        @Override
        public DataEntity getDataEntityById(Integer entityId) {
            return null;
        }

        @Override
        public DataEntity getDataEntityByName(String name) {
            return "asset".equals(name) ? entity() : null;
        }

        @Override
        public DataAttribute getDataAttributeById(Integer attributeId) {
            return null;
        }

        @Override
        public DataAttribute getDataAttributeByName(String entity, String attribute) {
            if ("asset".equals(entity) && "ip".equals(attribute)) {
                return attribute("ip", "src_ip", "String", List.of("equal", "in", "match", "isnull", "isnotnull"));
            }
            if ("asset".equals(entity) && "module_type_name".equals(attribute)) {
                return attribute("module_type_name", "module_type_name", "String", List.of("match"));
            }
            if ("asset".equals(entity) && "attack_type_name".equals(attribute)) {
                return attribute("attack_type_name", "attack_type_name", "String", List.of("match"));
            }
            return null;
        }

        @Override
        public List<DataEntity> getAllDataEntity() {
            return Collections.emptyList();
        }

        @Override
        public List<DataAttribute> getAllDataAttribute() {
            return Collections.emptyList();
        }

        @Override
        public List<DataAttribute> getAllDataAttributeByEntity(DataEntity dataEntity) {
            return Collections.emptyList();
        }

        @Override
        public DataOperator getDataOperatorByName(String name) {
            return List.of("equal", "notequal", "in", "match", "between", "greatthan", "lessthan", "greatequalthan", "lessequalthan", "isnull", "isnotnull").contains(name) ? operator(name) : null;
        }
    }
}
