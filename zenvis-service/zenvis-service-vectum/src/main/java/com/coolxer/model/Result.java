package com.coolxer.model;

import lombok.Data;

/**
 * 请求返回结果模型
 *
 * @author yaoqi.li
 * @date 2023/6/29 10:30
 */
@Data
public class Result {

  private final static Integer CODE_SUCCEED = 0;
  private final static Integer CODE_FAILED = 1;

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
  private Object data;

  public Result() {
  }

  public Result(Integer code, String msg, Object data) {
    this.code = code;
    this.msg = msg;
    this.data = data;
  }

  public static Result SUCCEED(String msg){
    return new Result(CODE_SUCCEED,msg,null);
  }

  public static Result SUCCEED(String msg,Object data){
    return new Result(CODE_SUCCEED,msg,data);
  }

  public static Result FAILED(String msg){
    return new Result(CODE_FAILED,msg,null);
  }

  public static Result FAILED(String msg,Object data){
    return new Result(CODE_FAILED,msg,data);
  }

}
