package com.coolxer.service.dih.agent;

import com.coolxer.service.dih.agent.nl2sql.connector.bo.ColumnInfoBO;
import com.coolxer.service.dih.agent.nl2sql.connector.bo.TableInfoBO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

class RedisVectorManagementServiceTest {

    @Test
    void convertColumnDocumentAllowsSamplesMetadata() {
        RedisVectorManagementService service = new RedisVectorManagementService();
        TableInfoBO table = TableInfoBO.builder()
                .name("risk_event")
                .description("风险事件")
                .build();
        ColumnInfoBO column = ColumnInfoBO.builder()
                .name("event_type")
                .tableName("risk_event")
                .description("事件类型")
                .type("String")
                .samples("[\"webshell\"]")
                .build();

        Document document = service.convertToDocument(table, column);

        assertThat(document.getMetadata())
                .containsEntry("samples", "[\"webshell\"]")
                .containsEntry("vectorType", "column")
                .containsEntry("tableName", "risk_event");
    }
}
