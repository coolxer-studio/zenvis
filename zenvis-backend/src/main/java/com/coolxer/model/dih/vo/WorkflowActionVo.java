package com.coolxer.model.dih.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class WorkflowActionVo {
    private boolean accepted;
    private String workflowId;
    private String state;
    private String partStatus;
    private Map<String, Object> continuation;
    private boolean retryable;
    private String extraData;
}
