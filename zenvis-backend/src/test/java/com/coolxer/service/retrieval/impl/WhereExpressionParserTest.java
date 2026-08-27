package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhereExpressionParserTest {

    private final WhereExpressionParser parser = new WhereExpressionParser();

    @Test
    void parsesBacktickIdentifierAndSmartQuotes() {
        WhereExpressionParser.WhereExpression expression = parser.parse("`attack_state` >= 1 and src_ip=‘10.0.0.1’");

        assertThat(expression.criteriaList()).hasSize(2);
        assertThat(expression.criteriaList().get(0).getAttribute()).isEqualTo("attack_state");
        assertThat(expression.criteriaList().get(0).getOperator()).isEqualTo("greatequalthan");
        assertThat(expression.criteriaList().get(1).getValueList()).containsExactly("10.0.0.1");
    }

    @Test
    void sharedParserIsSafeUnderConcurrentParsing() throws Exception {
        var executor = Executors.newFixedThreadPool(16);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                int value = i;
                tasks.add(() -> {
                    String field = "field_" + value;
                    WhereExpressionParser.WhereExpression parsed = parser.parse(
                            field + " = " + value + " or (`common_field` is not null and score >= " + value + ")");
                    assertThat(parsed.criteriaList()).hasSize(3);
                    assertThat(parsed.criteriaList().get(0).getAttribute()).isEqualTo(field);
                    assertThat(parsed.criteriaList().get(0).getValueList()).containsExactly(String.valueOf(value));
                    return parsed.normalizedExpression();
                });
            }
            assertThat(executor.invokeAll(tasks).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            })).hasSize(500);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void enforcesExpressionResourceLimits() {
        String tooManyConditions = String.join(" and ", java.util.Collections.nCopies(51, "field = 1"));
        assertThatThrownBy(() -> parser.parse(tooManyConditions))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("50");

        String tooDeep = "(".repeat(11) + "field = 1" + ")".repeat(11);
        assertThatThrownBy(() -> parser.parse(tooDeep))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("10");

        String tooManyInValues = "field in (" + String.join(",", java.util.Collections.nCopies(201, "1")) + ")";
        assertThatThrownBy(() -> parser.parse(tooManyInValues))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("200");
    }

    @Test
    void rejectsSqlFunctionsCommentsAndFieldlessConditions() {
        assertThatThrownBy(() -> parser.parse("lower(name) = 'x'"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parse("name = 'x' -- comment"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parse("1 = 1"))
                .isInstanceOf(ApiException.class);
    }
}
