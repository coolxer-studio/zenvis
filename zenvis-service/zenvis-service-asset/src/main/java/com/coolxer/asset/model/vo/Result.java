package com.coolxer.asset.model.vo;


import com.coolxer.asset.commons.enums.ResultCodeEnum;
import com.coolxer.asset.commons.exception.ApiException;
import lombok.Data;

/**
 * 请求返回结果模型
 *
 * @author yaoqi.li
 * @date 2023/6/29 10:30
 */
@Data
public class Result<T> {


  /**
   * 响应结果代码
   */
  private Integer code;

  /**
   * 提示消息(msg 是 message 的缩写，使用缩写是为了兼容原来的代码)
   */
  private String msg;

  /**
   * 数据
   */
  private T data;

  public Result() {
  }

  public Result(Integer code, String msg, T data) {
    this.code = code;
    this.msg = msg;
    this.data = data;
  }

  public Result(ResultCodeEnum resultCodeEnum, T data) {
    this.code = resultCodeEnum.getCode();
    this.msg = resultCodeEnum.getDescription();
    this.data = data;
  }


  /**
   * 构建请求成功时的响应对象。
   *
   * @param <T> 数据类型
   * @return 请求成功时的响应对象
   */
  public static <T> Result<T> success() {
    return new Result<>(ResultCodeEnum.SUCCESS, null);
  }

  /**
   * 构建请求成功时的响应对象。
   *
   * @param data 数据
   * @param <T>  数据类型
   * @return 请求成功时的响应对象
   */
  public static <T> Result<T> success(T data) {
    return new Result<>(ResultCodeEnum.SUCCESS, data);
  }

  /**
   * 构建请求成功时的响应对象。
   *
   * @param msg  提示信息
   * @param data 数据
   * @param <T>  数据类型
   * @return 请求成功时的响应对象
   */
  public static <T> Result<T> success(String msg, T data) {
    return new Result<>(ResultCodeEnum.SUCCESS.getCode(), msg, data);
  }

  /**
   * 构建请求失败的响应对象。
   *
   * @return 请求失败的响应对象
   */
  public static <T> Result<T> fail() {
    return new Result<>(ResultCodeEnum.INNER_ERROR, null);
  }


  /**
   * 构建请求失败的响应对象。
   *
   * @param resultCodeEnum 提示信息
   * @return 请求失败的响应对象
   */
  public static <T> Result<T> fail(ResultCodeEnum resultCodeEnum) {
    return new Result<>(resultCodeEnum, null);
  }

  public static <T> Result<T> fail(Exception e) {
    Result<T> result = new Result<>();
    if (e instanceof ApiException apiException) {
      result.setMsg(apiException.getDescription());
      result.setCode(apiException.getCode());

    } else {
      result.setCode(ResultCodeEnum.INNER_ERROR.getCode());
      result.setMsg(ResultCodeEnum.INNER_ERROR.getDescription());
    }
    return result;
  }


}
