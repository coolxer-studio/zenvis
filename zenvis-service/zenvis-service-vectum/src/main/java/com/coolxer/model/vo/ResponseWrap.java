package com.coolxer.model.vo;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import lombok.Data;

/**
 * 请求返回结果模型
 * <p>
 * 统一的API响应封装类，用于包装所有API返回结果
 * </p>
 */
@Data
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

    /**
     * 构造函数
     * @param status 状态码
     * @param msg 消息
     * @param data 数据
     */
    public ResponseWrap(Integer status, String msg, T data) {
        this.status = status;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 构造函数
     * @param resultCodeEnum 结果码枚举
     * @param data 数据
     */
    public ResponseWrap(ResultCodeEnum resultCodeEnum, T data) {
        this.status = resultCodeEnum.getCode();
        this.msg = resultCodeEnum.getDescription();
        this.data = data;
    }

    /**
     * 构建请求成功时的响应对象
     * @param <T> 数据类型
     * @return 请求成功时的响应对象
     */
    public static <T> ResponseWrap<T> success() {
        return new ResponseWrap<>(ResultCodeEnum.SUCCESS, null);
    }

    /**
     * 构建请求成功时的响应对象
     * @param data 数据
     * @param <T> 数据类型
     * @return 请求成功时的响应对象
     */
    public static <T> ResponseWrap<T> success(T data) {
        return new ResponseWrap<>(ResultCodeEnum.SUCCESS, data);
    }

    /**
     * 构建请求成功时的响应对象
     * @param msg 提示信息
     * @param data 数据
     * @param <T> 数据类型
     * @return 请求成功时的响应对象
     */
    public static <T> ResponseWrap<T> success(String msg, T data) {
        return new ResponseWrap<>(ResultCodeEnum.SUCCESS.getCode(), msg, data);
    }

    /**
     * 构建请求失败的响应对象
     * @param <T> 数据类型
     * @return 请求失败的响应对象
     */
    public static <T> ResponseWrap<T> fail() {
        return new ResponseWrap<>(ResultCodeEnum.INNER_ERROR, null);
    }

    /**
     * 构建请求失败的响应对象
     * @param resultCodeEnum 提示信息
     * @param <T> 数据类型
     * @return 请求失败的响应对象
     */
    public static <T> ResponseWrap<T> fail(ResultCodeEnum resultCodeEnum) {
        return new ResponseWrap<>(resultCodeEnum, null);
    }

    /**
     * 构建请求失败的响应对象
     * @param e 异常对象
     * @param <T> 数据类型
     * @return 请求失败的响应对象
     */
    public static <T> ResponseWrap<T> fail(Exception e) {
        ResponseWrap<T> responseWrap = new ResponseWrap<>();
        if (e instanceof ApiException apiException) {
            responseWrap.setMsg(apiException.getDescription());
            responseWrap.setStatus(apiException.getCode());

        } else {
            responseWrap.setStatus(ResultCodeEnum.INNER_ERROR.getCode());
            responseWrap.setMsg(ResultCodeEnum.INNER_ERROR.getDescription());
        }
        return responseWrap;
    }

}