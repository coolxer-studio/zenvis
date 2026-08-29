package com.coolxer.plugin.operation.model;

public class OperationBoardDto {
    private Integer id;
    private Long lastBoard;
    private String policy;
    private String event;
    private String metrics;
    private Object conditions;
    private String panelTitle;
    private String panelView;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Long getLastBoard() { return lastBoard; }
    public void setLastBoard(Long lastBoard) { this.lastBoard = lastBoard; }
    public String getPolicy() { return policy; }
    public void setPolicy(String policy) { this.policy = policy; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getMetrics() { return metrics; }
    public void setMetrics(String metrics) { this.metrics = metrics; }
    public Object getConditions() { return conditions; }
    public void setConditions(Object conditions) { this.conditions = conditions; }
    public String getPanelTitle() { return panelTitle; }
    public void setPanelTitle(String panelTitle) { this.panelTitle = panelTitle; }
    public String getPanelView() { return panelView; }
    public void setPanelView(String panelView) { this.panelView = panelView; }
}
