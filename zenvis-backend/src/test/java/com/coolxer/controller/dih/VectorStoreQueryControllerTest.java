package com.coolxer.controller.dih;

import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.base.dto.PageDto;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.service.dih.rag.RagDocumentManagementService;
import com.coolxer.service.dih.rag.RagDocumentManagementService.RagDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorStoreQueryControllerTest {

    @Test
    void listDocumentsRejectsWhenManagementDisabled() {
        VectorStoreQueryController controller = newController(false, new FakeRagDocumentManagementService());

        assertThatThrownBy(() -> controller.listDocuments(new PageDto(), null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("RAG文档管理接口未启用");
    }

    @Test
    void listDocumentsReturnsPagedPluginRagDocuments() {
        VectorStoreQueryController controller = newController(true, new FakeRagDocumentManagementService());
        PageDto pageDto = new PageDto();
        pageDto.setPage(1);
        pageDto.setPerPage(1);

        ResponseWrap<PageRowsVo<VectorStoreQueryController.RagDocumentVo>> response =
                controller.listDocuments(pageDto, "资产", "com_coolxer_asset");

        assertThat(response.getData().getTotal()).isEqualTo(2);
        assertThat(response.getData().getRows())
                .extracting(VectorStoreQueryController.RagDocumentVo::id)
                .containsExactly("doc-1");
    }

    @Test
    void deleteDocumentDelegatesToRagDocumentService() {
        FakeRagDocumentManagementService service = new FakeRagDocumentManagementService();
        VectorStoreQueryController controller = newController(true, service);

        controller.deleteDocumentById("doc-1");

        assertThat(service.deletedId).isEqualTo("doc-1");
    }

    private VectorStoreQueryController newController(boolean enabled, RagDocumentManagementService service) {
        VectorStoreQueryController controller = new VectorStoreQueryController();
        ReflectionTestUtils.setField(controller, "vectorStoreManagementEnabled", enabled);
        ReflectionTestUtils.setField(controller, "ragDocumentManagementService", service);
        return controller;
    }

    private static final class FakeRagDocumentManagementService extends RagDocumentManagementService {
        private String deletedId;

        @Override
        public List<RagDocument> listDocuments(String keyword, String source) {
            assertThat(keyword).isEqualTo("资产");
            assertThat(source).isEqualTo("com_coolxer_asset");
            return List.of(
                    new RagDocument("doc-1", "资产插件说明", Map.of("source", "com_coolxer_asset"), "com_coolxer_asset"),
                    new RagDocument("doc-2", "资产插件详情", Map.of("source", "com_coolxer_asset"), "com_coolxer_asset")
            );
        }

        @Override
        public boolean deleteDocumentById(String documentId) {
            this.deletedId = documentId;
            return true;
        }
    }
}
