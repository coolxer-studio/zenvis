package com.coolxer.plugin.asset.api;

public class PageQuery {
    private int perPage = 10;
    private int page = 1;
    private String orderBy;
    private String orderDir;

    public int getPerPage() { return perPage; }
    public void setPerPage(int perPage) { this.perPage = perPage; }
    public void setPer_page(int perPage) { this.perPage = perPage; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public String getOrderBy() { return orderBy; }
    public void setOrderBy(String orderBy) { this.orderBy = orderBy; }
    public String getOrderDir() { return orderDir; }
    public void setOrderDir(String orderDir) { this.orderDir = orderDir; }
}
