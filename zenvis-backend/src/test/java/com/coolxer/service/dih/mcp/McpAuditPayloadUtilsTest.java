package com.coolxer.service.dih.mcp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class McpAuditPayloadUtilsTest {

    @Test
    void utf8LengthMatchesJavaEncodingForAsciiChineseAndEmoji() {
        String value = "A中文🙂";

        assertThat(McpAuditPayloadUtils.utf8Length(value))
                .isEqualTo((long) value.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void truncationUsesByteBoundaryWithoutSplittingUnicodeCodePoints() {
        String value = "A中🙂B";

        assertThat(McpAuditPayloadUtils.truncateUtf8(value, 0)).isEmpty();
        assertThat(McpAuditPayloadUtils.truncateUtf8(value, 3)).isEqualTo("A");
        assertThat(McpAuditPayloadUtils.truncateUtf8(value, 4)).isEqualTo("A中");
        assertThat(McpAuditPayloadUtils.truncateUtf8(value, 7)).isEqualTo("A中");
        assertThat(McpAuditPayloadUtils.truncateUtf8(value, 8)).isEqualTo("A中🙂");
        assertThat(McpAuditPayloadUtils.truncateUtf8(value, 9)).isEqualTo(value);
    }

    @Test
    void truncationReturnsOriginalInstanceWhenValueFits() {
        String value = "完整结果";

        assertThat(McpAuditPayloadUtils.truncateUtf8(value, 1024)).isSameAs(value);
    }

    @Test
    void preparedResultKeepsLengthBeforeTruncation() {
        String value = "A中🙂B";

        McpAuditPayloadUtils.StoredPayload result = McpAuditPayloadUtils.prepareResult(value, 7);

        assertThat(result.value()).isEqualTo("A中");
        assertThat(result.originalLength())
                .isEqualTo((long) value.getBytes(StandardCharsets.UTF_8).length);
    }
}
