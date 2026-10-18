package com.coolxer.service.dih.rag;

import com.coolxer.commons.enums.RagKnowledgeBaseStatus;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.RagKnowledgeBase;
import com.coolxer.dao.mysql.repository.RagDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseDatasetRepository;
import com.coolxer.dao.mysql.repository.RagKnowledgeBaseRepository;
import com.coolxer.model.dih.dto.RagKnowledgeBaseDto;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    private RagKnowledgeBaseService newService(RagKnowledgeBaseRepository repository) {
        RagKnowledgeBaseService service = new RagKnowledgeBaseService();
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseRepository", repository);
        ReflectionTestUtils.setField(service, "ragKnowledgeBaseDatasetRepository",
                mock(RagKnowledgeBaseDatasetRepository.class));
        ReflectionTestUtils.setField(service, "ragDatasetRepository", mock(RagDatasetRepository.class));
        return service;
    }
}
