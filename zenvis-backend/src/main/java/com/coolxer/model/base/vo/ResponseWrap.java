package com.coolxer.model.base.vo;


import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 请求返回结果模型
 */
@Data
@Schema(description = "ZenVis 统一业务响应。HTTP 200 不代表业务成功，业务成功以 status=0 为准。")
public class ResponseWrap<T> {


    /**
     * 响应结果代码
     */
    @Schema(description = "业务状态码，0 表示成功，101 表示需要重新登录", example = "0")
    private Integer status;

    /**
     * 提示消息(msg 是 message 的缩写，使用缩写是为了兼容原来的代码)
     */
    @Schema(description = "业务提示消息", example = "请求成功")
    private String msg;

    /**
     * 数据
     */
    @Schema(description = "接口业务数据")
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

    public static <T> @NotNull ResponseWrap<T> fail(Exception e) {
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

    /**
     * 构建请求失败的响应对象。
     *
     * @param status HTTP状态码
     * @param msg    错误信息
     * @param <T>    数据类型
     * @return 请求失败的响应对象
     */
    public static <T> @NotNull ResponseWrap<T> fail(int status, String msg) {
        ResponseWrap<T> responseWrap = new ResponseWrap<>();
        responseWrap.setStatus(status);
        responseWrap.setMsg(msg);
        return responseWrap;
    }


}
