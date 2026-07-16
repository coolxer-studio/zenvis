package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.repository.RetrievalRuleRepository;
import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import com.coolxer.model.retrieval.dto.RequestDisplayDto;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.DataOperator;
import com.coolxer.model.retrieval.meta.MetaData;
import com.coolxer.model.retrieval.rule.PersistedRetrievalRule;
import com.coolxer.model.retrieval.rule.DisplayAttribute;
import com.coolxer.model.retrieval.rule.RetrievalCriteria;
import com.coolxer.model.retrieval.rule.RetrievalSql;
import com.coolxer.model.retrieval.vo.RetrievalRuleDetailVo;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.utils.JacksonUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalRuleLifecycleTest {

    @Test
    void createsCanonicalV2RuleOwnedByCurrentUser() {
        RetrievalRuleRepository repository = mock(RetrievalRuleRepository.class);
        RetrievalRuleServiceImpl service = service(repository);
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            com.coolxer.dao.mysql.entity.RetrievalRule saved = invocation.getArgument(0);
            saved.setId(42);
            return saved;
        });

        Integer id = service.createRule(request("my rule"), 7);

        assertThat(id).isEqualTo(42);
        ArgumentCaptor<com.coolxer.dao.mysql.entity.RetrievalRule> captor =
                ArgumentCaptor.forClass(com.coolxer.dao.mysql.entity.RetrievalRule.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreateBy()).isEqualTo(7);
        assertThat(JacksonUtil.toObject(captor.getValue().getRuleString(), PersistedRetrievalRule.class).getSchemaVersion())
                .isEqualTo(2);
    }

    @Test
    void partialUpdateMutatesExistingEntityAndPreservesAuditOwner() {
        RetrievalRuleRepository repository = mock(RetrievalRuleRepository.class);
        RetrievalRuleServiceImpl service = service(repository);
        com.coolxer.dao.mysql.entity.RetrievalRule existing = storedEntity(9, 7);
        when(repository.findByIdAndCreateBy(9, 7)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        RetrievalRequestDto update = new RetrievalRequestDto();
        update.setId(9);
        update.setRuleName("renamed");

        Integer id = service.updateRule(update, 7);

        assertThat(id).isEqualTo(9);
        assertThat(existing.getName()).isEqualTo("renamed");
        assertThat(existing.getCreateBy()).isEqualTo(7);
        PersistedRetrievalRule persisted = JacksonUtil.toObject(existing.getRuleString(), PersistedRetrievalRule.class);
        assertThat(persisted.getEntity()).isEqualTo("asset");
        assertThat(persisted.getDisplayList().get(0).getAttributeList()).containsExactly("ip");
    }

    @Test
    void changingEntityRequiresExplicitNewConfiguration() {
        RetrievalRuleRepository repository = mock(RetrievalRuleRepository.class);
        RetrievalRuleServiceImpl service = service(repository);
        when(repository.findByIdAndCreateBy(9, 7)).thenReturn(Optional.of(storedEntity(9, 7)));
        RetrievalRequestDto update = new RetrievalRequestDto();
        update.setId(9);
        update.setEntity("other");

        assertThatThrownBy(() -> service.updateRule(update, 7))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("展示字段");
    }

    @Test
    void emptyCriteriaListExplicitlyClearsNormalConditions() {
        RetrievalRuleRepository repository = mock(RetrievalRuleRepository.class);
        RetrievalRuleServiceImpl service = service(repository);
        com.coolxer.dao.mysql.entity.RetrievalRule existing = storedEntity(9, 7);
        when(repository.findByIdAndCreateBy(9, 7)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        RetrievalRequestDto update = new RetrievalRequestDto();
        update.setId(9);
        update.setCriteriaList(List.of());

        service.updateRule(update, 7);

        PersistedRetrievalRule persisted = JacksonUtil.toObject(existing.getRuleString(), PersistedRetrievalRule.class);
        assertThat(persisted.getCriteriaList()).isEmpty();
    }

    @Test
    void otherUsersCannotEnumerateRuleExistence() {
        RetrievalRuleRepository repository = mock(RetrievalRuleRepository.class);
        RetrievalRuleServiceImpl service = service(repository);
        when(repository.findByIdAndCreateBy(9, 8)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRuleById(9, 8))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不可用");
    }

    @Test
    void otherUsersCannotDeleteRule() {
        RetrievalRuleRepository repository = mock(RetrievalRuleRepository.class);
        RetrievalRuleServiceImpl service = service(repository);
        when(repository.findByIdAndCreateBy(9, 8)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteRule(9, 8))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不可用");
    }

    @Test
    void readsLegacyExpandedRuleWithoutMigratingDatabase() {
        RetrievalRuleRepository repository = mock(RetrievalRuleRepository.class);
        RetrievalRuleServiceImpl service = service(repository);
        com.coolxer.dao.mysql.entity.RetrievalRule entity = new com.coolxer.dao.mysql.entity.RetrievalRule();
        entity.setId(11);
        entity.setCreateBy(7);
        entity.setName("legacy");
        com.coolxer.model.retrieval.rule.RetrievalRule legacy = new com.coolxer.model.retrieval.rule.RetrievalRule();
        RetrievalCriteria criteria = new RetrievalCriteria();
        criteria.setEntity(((FakeMetaDataService) ReflectionTestUtils.getField(service, "metaDataService")).entity);
        criteria.setAttribute(((FakeMetaDataService) ReflectionTestUtils.getField(service, "metaDataService")).attribute);
        DataOperator operator = new DataOperator();
        operator.setName("equal");
        criteria.setOperator(operator);
        criteria.setValueList(List.of("10.0.0.1"));
        legacy.setRetrievalCriteria(List.of(criteria));
        legacy.setCriteriaLogic("and");
        DisplayAttribute display = new DisplayAttribute();
        display.setEntity(criteria.getEntity());
        display.setAttributeList(List.of(criteria.getAttribute()));
        legacy.setDisplayAttributes(List.of(display));
        entity.setRuleString(JacksonUtil.toJson(legacy));
        when(repository.findByIdAndCreateBy(11, 7)).thenReturn(Optional.of(entity));

        RetrievalRuleDetailVo detail = service.getRuleDetail(11, 7);

        assertThat(detail.getStatus()).isEqualTo("valid");
        assertThat(detail.getConfig().getEntity()).isEqualTo("asset");
        assertThat(detail.getConfig().getCriteriaList()).hasSize(1);
        assertThat(detail.getAttributeList()).singleElement()
                .extracting("linkTemplate")
                .isEqualTo("/asset/detail?ip={ip}");
    }

    @Test
    void marksLegacyFreeSqlRuleInvalidAndNeverHydratesItForExecution() {
        RetrievalRuleRepository repository = mock(RetrievalRuleRepository.class);
        RetrievalRuleServiceImpl service = service(repository);
        com.coolxer.dao.mysql.entity.RetrievalRule entity = new com.coolxer.dao.mysql.entity.RetrievalRule();
        entity.setId(12);
        entity.setCreateBy(7);
        entity.setName("legacy sql");
        com.coolxer.model.retrieval.rule.RetrievalRule legacy = new com.coolxer.model.retrieval.rule.RetrievalRule();
        RetrievalSql sql = new RetrievalSql();
        DataEntity dataEntity = new DataEntity();
        dataEntity.setName("asset");
        sql.setEntity(dataEntity);
        sql.setSql("select * from asset_table");
        legacy.setRetrievalSql(sql);
        entity.setRuleString(JacksonUtil.toJson(legacy));
        when(repository.findByIdAndCreateBy(12, 7)).thenReturn(Optional.of(entity));

        RetrievalRuleDetailVo detail = service.getRuleDetail(12, 7);

        assertThat(detail.getStatus()).isEqualTo("invalid");
        assertThat(detail.getIssues()).extracting("code").contains("LEGACY_SQL_DISABLED");
        assertThatThrownBy(() -> service.getRuleById(12, 7))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("自由SQL");
    }

    @Test
    void acceptsLegacyRuleIdJsonAlias() {
        RetrievalRequestDto dto = JacksonUtil.toObject("{\"rule_id\":99}", RetrievalRequestDto.class);
        assertThat(dto.getId()).isEqualTo(99);
    }

    private RetrievalRuleServiceImpl service(RetrievalRuleRepository repository) {
        RetrievalRuleServiceImpl service = new RetrievalRuleServiceImpl();
        ReflectionTestUtils.setField(service, "retrievalRuleRepository", repository);
        ReflectionTestUtils.setField(service, "metaDataService", new FakeMetaDataService());
        return service;
    }

    private RetrievalRequestDto request(String name) {
        RetrievalRequestDto request = new RetrievalRequestDto();
        request.setRuleName(name);
        request.setType("normal");
        request.setEntity("asset");
        RequestCriteriaDto criteria = new RequestCriteriaDto();
        criteria.setAttribute("ip");
        criteria.setOperator("equal");
        criteria.setValueList(List.of("10.0.0.1"));
        request.setCriteriaList(List.of(criteria));
        RequestDisplayDto display = new RequestDisplayDto();
        display.setEntity("asset");
        display.setAttributeList(List.of("ip"));
        request.setDisplayList(List.of(display));
        return request;
    }

    private com.coolxer.dao.mysql.entity.RetrievalRule storedEntity(int id, int owner) {
        com.coolxer.dao.mysql.entity.RetrievalRule entity = new com.coolxer.dao.mysql.entity.RetrievalRule();
        entity.setId(id);
        entity.setCreateBy(owner);
        entity.setName("old");
        RetrievalRequestDto request = request("old");
        PersistedRetrievalRule persisted = new PersistedRetrievalRule();
        persisted.setType(request.getType());
        persisted.setEntity(request.getEntity());
        persisted.setCriteriaList(request.getCriteriaList());
        persisted.setCriteriaLogic("and");
        persisted.setDisplayList(request.getDisplayList());
        entity.setRuleString(JacksonUtil.toJson(persisted));
        return entity;
    }

    private static class FakeMetaDataService implements MetaDataService {
        private final DataEntity entity;
        private final DataAttribute attribute;

        private FakeMetaDataService() {
            entity = new DataEntity();
            entity.setId(1);
            entity.setName("asset");
            entity.setTableName("asset_table");
            entity.setSortColumn("src_ip");
            attribute = new DataAttribute();
            attribute.setId(10);
            attribute.setEntity("asset");
            attribute.setName("ip");
            attribute.setLabel("IP");
            attribute.setColumnName("src_ip");
            attribute.setColumnType("String");
            attribute.setOperators(List.of("equal", "isnull"));
            attribute.setLinkTemplate("/asset/detail?ip={ip}");
        }

        @Override public MetaData loadMetaData() { return null; }
        @Override public DataEntity getDataEntityById(Integer entityId) { return entityId == 1 ? entity : null; }
        @Override public DataEntity getDataEntityByName(String name) { return "asset".equals(name) ? entity : null; }
        @Override public DataAttribute getDataAttributeById(Integer attributeId) { return attributeId == 10 ? attribute : null; }
        @Override public DataAttribute getDataAttributeByName(String entityName, String name) {
            return "asset".equals(entityName) && "ip".equals(name) ? attribute : null;
        }
        @Override public List<DataEntity> getAllDataEntity() { return List.of(entity); }
        @Override public List<DataAttribute> getAllDataAttribute() { return List.of(attribute); }
        @Override public List<DataAttribute> getAllDataAttributeByEntity(DataEntity dataEntity) { return List.of(attribute); }
        @Override public DataOperator getDataOperatorByName(String name) {
            if (!List.of("equal", "isnull").contains(name)) return null;
            DataOperator operator = new DataOperator();
            operator.setName(name);
            return operator;
        }
    }
}
