package com.coolxer.service.dih.rag;

import com.coolxer.service.dih.rag.RagDocumentManagementService.RagDocument;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagDocumentManagementServiceTest {

    @Test
    void listDocumentsFiltersBySourceAndKeyword() {
        FakeRagDocumentManagementService service = new FakeRagDocumentManagementService(List.of(
                new RagDocument("doc-1", "资产插件说明", Map.of("title", "资产"), "com_coolxer_asset"),
                new RagDocument("doc-2", "风险插件说明", Map.of("title", "风险"), "com_coolxer_risk"),
                new RagDocument("doc-3", "探针安装指南", Map.of("title", "探针"), "com_coolxer_probe")
        ));

        List<RagDocument> documents = service.listDocuments("风险", "com_coolxer_risk");

        assertThat(documents)
                .extracting(RagDocument::id)
                .containsExactly("doc-2");
    }

    @Test
    void searchDocumentsBoundsTopKAndKeepsSource() {
        FakeRagDocumentManagementService service = new FakeRagDocumentManagementService(List.of());

        service.searchDocuments("插件文档", 1000, "com_coolxer_asset");

        assertThat(service.capturedTopK).isEqualTo(100);
        assertThat(service.capturedSource).isEqualTo("com_coolxer_asset");
        assertThat(service.capturedQuery).isEqualTo("插件文档");
    }

    @Test
    void deleteDocumentsByIdsFiltersBlankIds() {
        FakeRagDocumentManagementService service = new FakeRagDocumentManagementService(List.of());

        boolean success = service.deleteDocumentsByIds(List.of("doc-1", "", "doc-2"));

        assertThat(success).isTrue();
        assertThat(service.deletedIds).containsExactly("doc-1", "doc-2");
    }

    @Test
    void fromRedisDocumentFallsBackToTextWhenContentIsMissing() {
        RagDocumentManagementService service = new RagDocumentManagementService();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("text", "插件使用说明");
        properties.put("metadata", "{\"source\":\"com_coolxer_asset\",\"title\":\"资产\"}");
        redis.clients.jedis.search.Document redisDocument =
                new redis.clients.jedis.search.Document("doc-1", properties);

        RagDocument document = service.fromRedisDocument(redisDocument);

        assertThat(document.id()).isEqualTo("doc-1");
        assertThat(document.text()).isEqualTo("插件使用说明");
        assertThat(document.source()).isEqualTo("com_coolxer_asset");
        assertThat(document.metadata()).containsEntry("title", "资产");
    }

    @Test
    void fromRedisDocumentUsesEmptyTextWhenContentAndTextAreNull() {
        RagDocumentManagementService service = new RagDocumentManagementService();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("content", null);
        properties.put("text", null);
        redis.clients.jedis.search.Document redisDocument =
                new redis.clients.jedis.search.Document("doc-1", properties);

        RagDocument document = service.fromRedisDocument(redisDocument);

        assertThat(document.text()).isEmpty();
    }

    @Test
    void fromRedisDocumentReadsRedisJsonRootDocument() {
        RagDocumentManagementService service = new RagDocumentManagementService();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("$", """
                {
                  "chunk_index": 0,
                  "source": "com_coolxer_plugin_user_event",
                  "title": "菜单概述",
                  "content": "用户事件插件说明",
                  "embedding": [0.1, 0.2]
                }
                """);
        redis.clients.jedis.search.Document redisDocument =
                new redis.clients.jedis.search.Document("doc-json-1", properties);

        RagDocument document = service.fromRedisDocument(redisDocument);

        assertThat(document.id()).isEqualTo("doc-json-1");
        assertThat(document.text()).isEqualTo("用户事件插件说明");
        assertThat(document.source()).isEqualTo("com_coolxer_plugin_user_event");
        assertThat(document.metadata())
                .containsEntry("chunk_index", 0)
                .containsEntry("title", "菜单概述")
                .containsEntry("source", "com_coolxer_plugin_user_event")
                .doesNotContainKeys("content", "embedding");
    }

    private static final class FakeRagDocumentManagementService extends RagDocumentManagementService {
        private final List<RagDocument> documents;
        private final List<String> deletedIds = new ArrayList<>();
        private String capturedQuery;
        private int capturedTopK;
        private String capturedSource;

        private FakeRagDocumentManagementService(List<RagDocument> documents) {
            this.documents = documents;
        }

        @Override
        protected List<RagDocument> loadAllDocuments() {
            return documents;
        }

        @Override
        protected boolean isVectorStoreAvailable(String operation) {
            return true;
        }

        @Override
        protected List<RagDocument> doSearchDocuments(String query, int topK, String source) {
            this.capturedQuery = query;
            this.capturedTopK = topK;
            this.capturedSource = source;
            return documents;
        }

        @Override
        public boolean deleteDocumentsByIds(List<String> documentIds) {
            this.deletedIds.addAll(documentIds.stream().filter(id -> id != null && !id.isBlank()).toList());
            return true;
        }
    }
}
