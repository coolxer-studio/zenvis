package com.coolxer.plugin.controller;

/**
 * 资产主机数据传输对象
 */
public class UserSearchDto {

    /**
     * ID
     */
    private String id;

    /**
     * 用户名称
     */
    private String name;

    /**
     * 身份编码
     */
    private String card;

    /**
     * 排序字段
     */
    private String orderBy;

    /**
     * 排序方式
     */
    private String orderDir;

    /**
     * 每页显示条数，默认 10
     */
    private int perPage = 10;

    /**
     * 当前页
     */
    private int page = 1;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCard() { return card; }
    public void setCard(String card) { this.card = card; }
    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }
    public String getOrderDir() { return orderDir; }
    public void setOrderDir(String orderDir) { this.orderDir = orderDir; }
    public int getPerPage() { return perPage; }
    public void setPerPage(int perPage) { this.perPage = perPage; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }


}
