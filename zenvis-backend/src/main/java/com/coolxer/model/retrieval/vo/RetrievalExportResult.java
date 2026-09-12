package com.coolxer.model.retrieval.vo;

/**
 * Global retrieval CSV export payload and metadata.
 */
public record RetrievalExportResult(
        byte[] content,
        int exportedRows,
        boolean truncated,
        int limit) {
}
