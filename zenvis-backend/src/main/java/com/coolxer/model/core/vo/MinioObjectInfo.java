package com.coolxer.model.core.vo;

import java.time.Instant;

/**
 * MinIO 对象元信息。
 */
public record MinioObjectInfo(
        long size,
        String contentType,
        String etag,
        Instant lastModified
) {
}
