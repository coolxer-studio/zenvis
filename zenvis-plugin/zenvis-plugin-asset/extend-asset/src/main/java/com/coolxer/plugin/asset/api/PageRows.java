package com.coolxer.plugin.asset.api;

import java.util.List;

public class PageRows<T> {
    private List<T> rows;
    private long total;

    public PageRows() {}
    public PageRows(List<T> rows, long total) { this.rows = rows; this.total = total; }
    public List<T> getRows() { return rows; }
    public void setRows(List<T> rows) { this.rows = rows; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
