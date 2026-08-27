package com.coolxer.controller.retrieval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregateRemovalTest {

    @Test
    void aggregateBackendTypesAreRemoved() {
        assertThatThrownBy(() -> Class.forName(
                "com.coolxer.controller.retrieval." + "Aggregate" + "Controller"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(
                "com.coolxer.service.retrieval." + "Aggregate" + "Service"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName(
                "com.coolxer.service.retrieval.impl." + "Aggregate" + "ServiceImpl"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void retrievalMcpDoesNotExposeAggregateTools() {
        assertThat(Arrays.stream(RetrievalMcpTool.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(Objects::nonNull)
                .map(Tool::name))
                .doesNotContain("retrieval_msg_" + "tag", "retrieval_msg_" + "trend");
    }
}
