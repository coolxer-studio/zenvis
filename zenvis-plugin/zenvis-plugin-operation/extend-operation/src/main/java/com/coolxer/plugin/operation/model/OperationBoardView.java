package com.coolxer.plugin.operation.model;

public record OperationBoardView(
        Long id,
        String panelIcon,
        String panelTitle,
        String panelView
) {
    public static OperationBoardView from(OperationBoardRecord record) {
        return new OperationBoardView(record.id(), record.icon(), record.title(), record.view());
    }
}
