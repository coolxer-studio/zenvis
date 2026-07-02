package com.coolxer.service.dih.agent.nl2sql.service.base;

import com.coolxer.service.dih.agent.nl2sql.request.SearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaseVectorStoreServiceTest {

    private final BaseVectorStoreService service = new BaseVectorStoreService() {
        @Override
        protected EmbeddingModel getEmbeddingModel() {
            return null;
        }

        @Override
        public List<Document> searchWithVectorType(SearchRequest searchRequest) {
            return List.of();
        }

        @Override
        public List<Document> searchWithFilter(SearchRequest searchRequest) {
            return List.of();
        }
    };

    @Test
    void filterDocumentsForAgentKeepsLegacyDocsAndMatchingAgentDocs() {
        Document legacy = new Document("legacy", "legacy", Map.of("vectorType", "table"));
        Document matching = new Document("matching", "matching", Map.of("agentId", "agent_inspect"));
        Document other = new Document("other", "other", Map.of("agentId", "agent_report"));

        List<Document> filtered = service.filterDocumentsForAgent(List.of(legacy, matching, other), "agent_inspect");

        assertThat(filtered).extracting(Document::getId)
                .containsExactly("legacy", "matching");
    }
}
