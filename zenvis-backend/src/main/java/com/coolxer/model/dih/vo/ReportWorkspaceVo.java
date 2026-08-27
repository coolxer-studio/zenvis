package com.coolxer.model.dih.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportWorkspaceVo {
    private Map<String, Object> currentDocument;
    private List<Map<String, Object>> revisions;
    private List<Map<String, Object>> artifacts;
    private String extraData;
}
