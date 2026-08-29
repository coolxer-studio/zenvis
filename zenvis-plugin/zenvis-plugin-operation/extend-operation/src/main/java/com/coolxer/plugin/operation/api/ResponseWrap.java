package com.coolxer.plugin.operation.api;

public class ResponseWrap<T> {
    private Integer status;
    private String msg;
    private T data;

    public ResponseWrap() {}
    public ResponseWrap(Integer status, String msg, T data) { this.status = status; this.msg = msg; this.data = data; }
    public static <T> ResponseWrap<T> success(T data) { return new ResponseWrap<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getDescription(), data); }
    public static <T> ResponseWrap<T> fail() { return fail(ResultCode.INNER_ERROR); }
    public static <T> ResponseWrap<T> fail(ResultCode resultCode) { return new ResponseWrap<>(resultCode.getCode(), resultCode.getDescription(), null); }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
