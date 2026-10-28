package com.coolxer.service.dih.attachment;

import java.util.Set;

/**
 * 附件解析生命周期及发送约束。
 */
public final class AttachmentStatus {

    public static final String PENDING = "pending";
    public static final String PROCESSING = "processing";
    public static final String READY = "ready";
    public static final String PARTIAL = "partial";
    public static final String FAILED = "failed";
    public static final String UNSUPPORTED = "unsupported";

    private static final Set<String> BLOCKING = Set.of(PENDING, PROCESSING, FAILED);

    private AttachmentStatus() {
    }

    public static boolean blocksSend(String status) {
        return status != null && BLOCKING.contains(status.toLowerCase());
    }

    public static boolean terminal(String status) {
        return READY.equals(status) || PARTIAL.equals(status)
                || FAILED.equals(status) || UNSUPPORTED.equals(status);
    }

    public static String normalize(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        String value = status.trim().toLowerCase();
        return switch (value) {
            case "readable", "image", "uploaded" -> READY;
            default -> value;
        };
    }
}
