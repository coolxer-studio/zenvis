package com.coolxer.risk.commons.enums;

import lombok.Getter;

/**
 * 接口结果返回码枚举类
 *
 * @author yaoqi.li
 * @date 2023/6/29 10:23
 */
@Getter
public enum ResultCodeEnum {

  /**
   * 成功
   */
  SUCCESS(0, "请求成功"),

  /**
   * 未知错误
   */
  UNKNOWN_ERROR(-1, "未知错误"),

  /**
   * 服务器内部错误
   */
  INNER_ERROR(1, "请求失败"),

  /**
   * 非法参数
   */
  ILLEGAL_PARAMETERS(101, "非法参数！"),

  /**
   * 未知的评分规则
   */
  UNKNOWN_RATING_RULE(102, "未知的评分规则！");

  private final int code;
  private final String description;

  ResultCodeEnum(int code, String description) {
    this.code = code;
    this.description = description;
  }


}
