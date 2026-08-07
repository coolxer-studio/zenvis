package com.coolxer.plugin.asset;

import com.coolxer.plugin.asset.model.AssetRuleDto;
import com.coolxer.plugin.asset.repository.AssetRuleRepository;
import com.coolxer.plugin.asset.service.AssetRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssetRuleProxyContractTest {

    @Test
    void dynamicPluginBeansDoNotRequireCglibSubclassing() throws Exception {
        assertThat(AssetRuleRepository.class).hasAnnotation(Component.class);
        assertThat(AssetRuleRepository.class.isAnnotationPresent(Repository.class)).isFalse();

        assertThat(AssetRuleService.class.getMethod("add", AssetRuleDto.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(AssetRuleService.class.getMethod("update", long.class, AssetRuleDto.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(AssetRuleService.class.getMethod("delete", long.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(AssetRuleService.class.getMethod("deleteAll", List.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
    }
}
