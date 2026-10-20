package com.coolxer.service.dih.rag;

import com.coolxer.commons.enums.RagKnowledgeBaseStatus;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.RagDataset;
import com.coolxer.dao.mysql.entity.RagKnowledgeBase;
import com.coolxer.dao.mysql.entity.RagKnowledgeBaseDataset;
import com.coolxer.dao.mysql.repository.RagDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseRepository;
import com.coolxer.model.dih.dto.RagKnowledgeBaseDto;
import com.coolxer.service.core.MinioService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识库名称唯一性校验测试
 */
class RagKnowledgeBaseServiceTest {

    @Test
    void createThrowsWhenNameDuplicated() {
        RagKnowledgeBaseRepository repository = mock(RagKnowledgeBaseRepository.class);
        when(repository.countByName("测试知识库", null)).thenReturn(1L);
        RagKnowledgeBaseService service = newService(repository);

        RagKnowledgeBaseDto dto = new RagKnowledgeBaseDto();
        dto.setName("测试知识库");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("知识库名称已存在");
    }

    @Test
    void updateThrowsWhenNameDuplicated() {
        RagKnowledgeBaseRepository repository = mock(RagKnowledgeBaseRepository.class);
        RagKnowledgeBase existing = new RagKnowledgeBase().setName("旧名称");
        existing.setIsDelete(0);
        existing.setStatus(RagKnowledgeBaseStatus.UNBUILD);
        when(repository.findById(1)).thenReturn(Optional.of(existing));
        when(repository.countByName("测试知识库", 1)).thenReturn(1L);
        RagKnowledgeBaseService service = newService(repository);

        RagKnowledgeBaseDto dto = new RagKnowledgeBaseDto();
        dto.setName("测试知识库");
        assertThatThrownBy(() -> service.update(1, dto))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("知识库名称已存在");
    }

    @Test
    void createPassesWhenNameUnique() {
        RagKnowledgeBaseRepository repository = mock(RagKnowledgeBaseRepository.class);
        when(repository.countByName("测试知识库", null)).thenReturn(0L);
        when(repository.save(any(RagKnowledgeBase.class))).thenAnswer(invocation -> {
            RagKnowledgeBase kb = invocation.getArgument(0);
            kb.setId(1);
            return kb;
        });
        RagKnowledgeBaseService service = newService(repository);

        RagKnowledgeBaseDto dto = new RagKnowledgeBaseDto();
        dto.setName("测试知识库");
        dto.setDatasetIds(List.of());
        RagKnowledgeBase created = service.create(dto);
        assertThat(created.getName()).isEqualTo("测试知识库");
    }

    @Test
    void buildNowBuildsSynchronouslyAndClearsFingerprint() {
        RagKnowledgeBaseRepository repository = mock(RagKnowledgeBaseRepository.class);
        RagKnowledgeBaseDatasetRepository relationRepository = mock(RagKnowledgeBaseDatasetRepository.class);
        RagDatasetRepository datasetRepository = mock(RagDatasetRepository.class);
        VectorStoreInitializerService vectorStoreInitializerService = mock(VectorStoreInitializerService.class);
        MinioService minioService = mock(MinioService.class);
        VectorStoreDelegate vectorStoreDelegate = mock(VectorStoreDelegate.class);
        RagKnowledgeBase knowledgeBase = new RagKnowledgeBase().setName("知识库")
                .setStatus(RagKnowledgeBaseStatus.UNBUILD)
                .setBuildFingerprint("stale");
        knowledgeBase.setId(1);
        knowledgeBase.setIsDelete(0);
        List<String> saveStates = new ArrayList<>();
        RagDataset dataset = new RagDataset().setName("数据集").setDirectory("rag/dataset");
        dataset.setId(2);
        dataset.setIsDelete(0);
        when(repository.findById(1)).thenReturn(Optional.of(knowledgeBase));
        when(repository.save(any(RagKnowledgeBase.class))).thenAnswer(invocation -> {
            RagKnowledgeBase saved = invocation.getArgument(0);
            saveStates.add(saved.getStatus() + ":" + saved.getBuildFingerprint());
            return saved;
        });
        when(relationRepository.findByKnowledgeBaseId(1)).thenReturn(List.of(
                new RagKnowledgeBaseDataset().setKnowledgeBaseId(1).setDatasetId(2)));
        when(datasetRepository.findAllById(any())).thenReturn(List.of(dataset));
        when(vectorStoreInitializerService.isRagAvailable()).thenReturn(true);
        when(minioService.listObjects("rag/dataset", null)).thenReturn(List.of());
        when(vectorStoreDelegate.getVectorStore("redis")).thenReturn(mock(VectorStore.class));
        RagKnowledgeBaseService service = newService(repository);
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseDatasetRepository", relationRepository);
        ReflectionTestUtils.setField(service, "ragDatasetRepository", datasetRepository);
        ReflectionTestUtils.setField(service, "vectorStoreInitializerService", vectorStoreInitializerService);
        ReflectionTestUtils.setField(service, "minioService", minioService);
        ReflectionTestUtils.setField(service, "vectorStoreDelegate", vectorStoreDelegate);

        RagKnowledgeBaseStatus status = service.buildNow(1);

        assertThat(status).isEqualTo(RagKnowledgeBaseStatus.BUILT);
        assertThat(knowledgeBase.getStatus()).isEqualTo(RagKnowledgeBaseStatus.BUILT);
        assertThat(knowledgeBase.getBuildFingerprint()).isNull();
        assertThat(saveStates).startsWith("BUILDING:null");
    }

    @Test
    void resolvesManagedKnowledgeBaseIdsByPackageAndCodes() {
        RagKnowledgeBaseRepository repository = mock(RagKnowledgeBaseRepository.class);
        RagKnowledgeBase first = new RagKnowledgeBase().setName("研判知识库")
                .setPluginPackage("com.acme.demo").setPluginCode("tracehub-kb");
        first.setId(9);
        RagKnowledgeBase second = new RagKnowledgeBase().setName("其他知识库")
                .setPluginPackage("com.acme.demo").setPluginCode("other-kb");
        second.setId(3);
        RagKnowledgeBase third = new RagKnowledgeBase().setName("第二研判知识库")
                .setPluginPackage("com.acme.demo").setPluginCode("tracehub-kb");
        third.setId(5);
        when(repository.findAllManaged("com.acme.demo")).thenReturn(List.of(first, second, third));
        RagKnowledgeBaseService service = newService(repository);

        assertThat(service.resolveManagedKnowledgeBaseIds(
                "com.acme.demo", List.of("tracehub-kb", "missing-kb")))
                .containsExactly(5, 9);
        assertThat(service.resolveManagedKnowledgeBaseIds("com.acme.demo", List.of()))
                .isEmpty();
    }

    private RagKnowledgeBaseService newService(RagKnowledgeBaseRepository repository) {
        RagKnowledgeBaseService service = new RagKnowledgeBaseService();
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseRepository", repository);
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseDatasetRepository",
                mock(RagKnowledgeBaseDatasetRepository.class));
        ReflectionTestUtils.setField(service, "ragDatasetRepository", mock(RagDatasetRepository.class));
        return service;
    }
}
