package com.coolxer.plugin.operation;

import com.coolxer.plugin.operation.model.OperationBoardDto;
import com.coolxer.plugin.operation.repository.OperationBoardRepository;
import com.coolxer.plugin.operation.service.OperationBoardService;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class OperationBoardProxyContractTest {

    @Test
    void dynamicPluginBeansDoNotRequireCglibSubclassing() throws Exception {
        assertThat(OperationBoardRepository.class).hasAnnotation(Component.class);
        assertThat(OperationBoardRepository.class.isAnnotationPresent(Repository.class)).isFalse();

        assertThat(OperationBoardService.class.getMethod("add", OperationBoardDto.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(OperationBoardService.class.getMethod("delete", long.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
    }
}
