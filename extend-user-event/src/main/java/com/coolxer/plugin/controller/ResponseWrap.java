package com.coolxer.plugin.controller;


/**
 * 请求返回结果模型
 *
 * @author hunter
 */
public class ResponseWrap<T> {


    /**
     * 响应结果代码
     */
    private Integer status;

    /**
     * 提示消息(msg 是 message 的缩写，使用缩写是为了兼容原来的代码)
     */
    private String msg;

    /**
     * 数据
     */
    private T data;

    public ResponseWrap() {
    }

    public ResponseWrap(Integer status, String msg, T data) {
        this.status = status;
        this.msg = msg;
        this.data = data;
    }

    public ResponseWrap(ResultCodeEnum resultCodeEnum, T data) {
        this.status = resultCodeEnum.getCode();
        this.msg = resultCodeEnum.getDescription();
        this.data = data;
    }


    /**
     * 构建请求成功时的响应对象。
     *
     * @param <T> 数据类型
     * @return 请求成功时的响应对象
     */
    public static <T> ResponseWrap<T> success() {
        return new ResponseWrap<>(ResultCodeEnum.SUCCESS, null);
    }

    /**
     * 构建请求成功时的响应对象。
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 请求成功时的响应对象
     */
    public static <T> ResponseWrap<T> success(T data) {
        return new ResponseWrap<>(ResultCodeEnum.SUCCESS, data);
    }

    /**
     * 构建请求成功时的响应对象。
     *
     * @param msg  提示信息
     * @param data 数据
     * @param <T>  数据类型
     * @return 请求成功时的响应对象
     */
    public static <T> ResponseWrap<T> success(String msg, T data) {
        return new ResponseWrap<>(ResultCodeEnum.SUCCESS.getCode(), msg, data);
    }

    /**
     * 构建请求失败的响应对象。
     *
     * @return 请求失败的响应对象
     */
    public static <T> ResponseWrap<T> fail() {
        return new ResponseWrap<>(ResultCodeEnum.INNER_ERROR, null);
    }


    /**
     * 构建请求失败的响应对象。
     *
     * @param resultCodeEnum 提示信息
     * @return 请求失败的响应对象
     */
    public static <T> ResponseWrap<T> fail(ResultCodeEnum resultCodeEnum) {
        return new ResponseWrap<>(resultCodeEnum, null);
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }


}
