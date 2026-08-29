package com.coolxer.plugin.risk.api;

public class ResponseWrap<T> {
    private Integer status;
    private String msg;
    private T data;

    public ResponseWrap() {}
    public ResponseWrap(Integer status, String msg, T data) { this.status = status; this.msg = msg; this.data = data; }
    public static <T> ResponseWrap<T> success(T data) { return new ResponseWrap<>(0, "请求成功", data); }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
