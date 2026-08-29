package com.coolxer.plugin.asset.service;

import com.coolxer.plugin.asset.model.AssetRuleDto;
import com.coolxer.plugin.asset.model.AssetRuleSearchQuery;
import com.coolxer.plugin.asset.model.AssetRuleStatus;
import com.coolxer.plugin.asset.model.AssetRuleView;
import com.coolxer.plugin.asset.repository.AssetRuleRepository;
import com.coolxer.plugin.asset.api.PageRows;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class AssetRuleService {

    private final AssetRuleRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public AssetRuleService(
            AssetRuleRepository repository,
            ObjectMapper objectMapper,
            @Qualifier("pluginMysqlTransactionManager") PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean add(AssetRuleDto dto) {
        String status = dto.getStatus() == null
                ? AssetRuleStatus.INACTIVE.name()
                : dto.getStatus().name();
        return Boolean.TRUE.equals(transactionTemplate.execute(
                ignored -> repository.insert(dto, status) > 0));
    }

    public boolean update(long id, AssetRuleDto dto) {
        return Boolean.TRUE.equals(transactionTemplate.execute(
                ignored -> repository.update(id, dto)));
    }

    public void delete(long id) {
        transactionTemplate.executeWithoutResult(ignored -> repository.deleteById(id));
    }

    public void deleteAll(List<Long> ids) {
        transactionTemplate.executeWithoutResult(ignored -> repository.deleteAllById(ids));
    }

    public AssetRuleView get(long id) {
        return repository.findById(id)
                .map(record -> AssetRuleView.from(record, objectMapper))
                .orElse(null);
    }

    public PageRows<AssetRuleView> page(AssetRuleSearchQuery query) {
        List<AssetRuleView> rows = repository.findPage(query).stream()
                .map(record -> AssetRuleView.from(record, objectMapper))
                .toList();
        return new PageRows<>(rows, repository.count(query));
    }
}
