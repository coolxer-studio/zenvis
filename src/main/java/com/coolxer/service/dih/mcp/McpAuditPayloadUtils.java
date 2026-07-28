package com.coolxer.service.dih.mcp;

/**
 * MCP 调用审计参数、结果的 UTF-8 长度与数据库容量适配工具。
 */
final class McpAuditPayloadUtils {

    static final long MYSQL_LONGTEXT_MAX_BYTES = 4_294_967_295L;

    private McpAuditPayloadUtils() {
    }

    static String fitLongText(String value) {
        return truncateUtf8(value, MYSQL_LONGTEXT_MAX_BYTES);
    }

    static StoredPayload prepareResult(String value) {
        return prepareResult(value, MYSQL_LONGTEXT_MAX_BYTES);
    }

    static StoredPayload prepareResult(String value, long maxBytes) {
        if (value == null) {
            return new StoredPayload(null, null);
        }
        long originalLength = utf8Length(value);
        return new StoredPayload(truncateUtf8(value, maxBytes), originalLength);
    }

    static long utf8Length(String value) {
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        long length = 0L;
        for (int index = 0; index < value.length(); ) {
            char current = value.charAt(index);
            if (current <= 0x7F) {
                length += 1L;
                index++;
            } else if (current <= 0x7FF) {
                length += 2L;
                index++;
            } else if (Character.isHighSurrogate(current)
                    && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                length += 4L;
                index += 2;
            } else if (Character.isSurrogate(current)) {
                // StandardCharsets.UTF_8 replaces an unpaired surrogate with '?'.
                length += 1L;
                index++;
            } else {
                length += 3L;
                index++;
            }
        }
        return length;
    }

    static String truncateUtf8(String value, long maxBytes) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (maxBytes <= 0L) {
            return "";
        }
        long length = 0L;
        int end = 0;
        while (end < value.length()) {
            char current = value.charAt(end);
            int charCount;
            long charBytes;
            if (current <= 0x7F) {
                charCount = 1;
                charBytes = 1L;
            } else if (current <= 0x7FF) {
                charCount = 1;
                charBytes = 2L;
            } else if (Character.isHighSurrogate(current)
                    && end + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(end + 1))) {
                charCount = 2;
                charBytes = 4L;
            } else if (Character.isSurrogate(current)) {
                charCount = 1;
                charBytes = 1L;
            } else {
                charCount = 1;
                charBytes = 3L;
            }
            if (length > maxBytes - charBytes) {
                break;
            }
            length += charBytes;
            end += charCount;
        }
        return end == value.length() ? value : value.substring(0, end);
    }

    record StoredPayload(String value, Long originalLength) {
    }
}
