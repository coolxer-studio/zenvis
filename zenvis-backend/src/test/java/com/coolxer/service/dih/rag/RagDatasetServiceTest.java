package com.coolxer.service.dih.rag;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.commons.enums.RagKnowledgeBaseStatus;
import com.coolxer.dao.mysql.entity.RagDataset;
import com.coolxer.dao.mysql.entity.RagKnowledgeBase;
import com.coolxer.dao.mysql.entity.RagKnowledgeBaseDataset;
import com.coolxer.dao.mysql.repository.RagDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseRepository;
import com.coolxer.model.dih.dto.RagDatasetDto;
import com.coolxer.service.core.MinioService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.List;

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

    @Test
    void updateInvalidatesManagedKnowledgeBaseFingerprint() {
        RagDatasetRepository repository = mock(RagDatasetRepository.class);
        RagKnowledgeBaseDatasetRepository relationRepository = mock(RagKnowledgeBaseDatasetRepository.class);
        RagKnowledgeBaseRepository knowledgeBaseRepository = mock(RagKnowledgeBaseRepository.class);
        RagDataset existing = new RagDataset().setName("旧名称");
        existing.setId(1);
        existing.setIsDelete(0);
        RagKnowledgeBase knowledgeBase = new RagKnowledgeBase().setName("知识库")
                .setPluginPackage("com.acme.demo").setPluginCode("kb")
                .setStatus(RagKnowledgeBaseStatus.BUILT)
                .setBuildFingerprint("stale-fingerprint");
        knowledgeBase.setId(2);
        when(repository.findById(1)).thenReturn(Optional.of(existing));
        when(repository.countByName("新名称", 1)).thenReturn(0L);
        when(repository.save(any(RagDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(relationRepository.findByDatasetId(1)).thenReturn(List.of(
                new RagKnowledgeBaseDataset().setKnowledgeBaseId(2).setDatasetId(1)));
        when(knowledgeBaseRepository.findById(2)).thenReturn(Optional.of(knowledgeBase));
        when(knowledgeBaseRepository.save(any(RagKnowledgeBase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        RagDatasetService service = newService(repository, relationRepository, knowledgeBaseRepository);

        RagDatasetDto dto = new RagDatasetDto();
        dto.setName("新名称");
        service.update(1, dto);

        assertThat(knowledgeBase.getBuildFingerprint()).isNull();
        assertThat(knowledgeBase.getStatus()).isEqualTo(RagKnowledgeBaseStatus.UNBUILD);
    }

    private RagDatasetService newService(RagDatasetRepository repository) {
        return newService(repository, mock(RagKnowledgeBaseDatasetRepository.class),
                mock(RagKnowledgeBaseRepository.class));
    }

    private RagDatasetService newService(RagDatasetRepository repository,
                                         RagKnowledgeBaseDatasetRepository relationRepository,
                                         RagKnowledgeBaseRepository knowledgeBaseRepository) {
        RagDatasetService service = new RagDatasetService();
        ReflectionTestUtils.setField(service, "ragDatasetRepository", repository);
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseDatasetRepository", relationRepository);
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseRepository", knowledgeBaseRepository);
        ReflectionTestUtils.setField(service, "minioService", mock(MinioService.class));
        return service;
    }
}
