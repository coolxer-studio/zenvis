package com.coolxer.plugin.operation.model;

public record OperationBoardRecord(
        Long id,
        Long lastBoard,
        Long nextBoard,
        String policy,
        String event,
        String metrics,
        String conditions,
        String icon,
        String title,
        String view
) {
}
