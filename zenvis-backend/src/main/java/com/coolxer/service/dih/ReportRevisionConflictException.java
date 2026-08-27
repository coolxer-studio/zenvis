package com.coolxer.service.dih;

import java.util.Map;

public class ReportRevisionConflictException extends RuntimeException {

    private final Map<String, Object> currentDocument;

    public ReportRevisionConflictException(Map<String, Object> currentDocument) {
        super("报表已被其他操作更新，请刷新后比较或合并。");
        this.currentDocument = currentDocument;
    }

    public Map<String, Object> getCurrentDocument() {
        return currentDocument;
    }
}
