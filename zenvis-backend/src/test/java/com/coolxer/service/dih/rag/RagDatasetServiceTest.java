package com.coolxer.service.dih.rag;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.RagDataset;
import com.coolxer.dao.mysql.repository.RagDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseDatasetRepository;
import com.coolxer.model.dih.dto.RagDatasetDto;
import com.coolxer.service.core.MinioService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据集名称唯一性校验测试
 */
class RagDatasetServiceTest {

    @Test
    void createThrowsWhenNameDuplicated() {
        RagDatasetRepository repository = mock(RagDatasetRepository.class);
        when(repository.countByName("测试数据集", null)).thenReturn(1L);
        RagDatasetService service = newService(repository);

        RagDatasetDto dto = new RagDatasetDto();
        dto.setName("测试数据集");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("数据集名称已存在");
    }

    @Test
    void updateThrowsWhenNameDuplicated() {
        RagDatasetRepository repository = mock(RagDatasetRepository.class);
        RagDataset existing = new RagDataset().setName("旧名称");
        existing.setIsDelete(0);
        when(repository.findById(1)).thenReturn(Optional.of(existing));
        when(repository.countByName("测试数据集", 1)).thenReturn(1L);
        RagDatasetService service = newService(repository);

        RagDatasetDto dto = new RagDatasetDto();
        dto.setName("测试数据集");
        assertThatThrownBy(() -> service.update(1, dto))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("数据集名称已存在");
    }

    @Test
    void createPassesWhenNameUnique() {
        RagDatasetRepository repository = mock(RagDatasetRepository.class);
        when(repository.countByName("测试数据集", null)).thenReturn(0L);
        when(repository.save(any(RagDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RagDatasetService service = newService(repository);

        RagDatasetDto dto = new RagDatasetDto();
        dto.setName("测试数据集");
        RagDataset created = service.create(dto);
        assertThat(created.getName()).isEqualTo("测试数据集");
    }

    @Test
    void updateAllowsSameNameOfSelf() {
        RagDatasetRepository repository = mock(RagDatasetRepository.class);
        RagDataset existing = new RagDataset().setName("测试数据集");
        existing.setIsDelete(0);
        when(repository.findById(1)).thenReturn(Optional.of(existing));
        when(repository.countByName("测试数据集", 1)).thenReturn(0L);
        when(repository.save(any(RagDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RagDatasetService service = newService(repository);

        RagDatasetDto dto = new RagDatasetDto();
        dto.setName("测试数据集");
        RagDataset updated = service.update(1, dto);
        assertThat(updated.getName()).isEqualTo("测试数据集");
    }

    private RagDatasetService newService(RagDatasetRepository repository) {
        RagDatasetService service = new RagDatasetService();
        ReflectionTestUtils.setField(service, "ragDatasetRepository", repository);
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseDatasetRepository",
                mock(RagKnowledgeBaseDatasetRepository.class));
        ReflectionTestUtils.setField(service, "minioService", mock(MinioService.class));
        return service;
    }
}
