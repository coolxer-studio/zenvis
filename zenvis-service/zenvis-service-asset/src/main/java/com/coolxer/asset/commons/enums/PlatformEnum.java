package com.coolxer.asset.commons.enums;


/**
 * @author yaoqi.li
 */

public enum PlatformEnum {
  /**
   * android
   */
  ANDROID("android"),
  /**
   * ios
   */
  IOS("ios"),
  /**
   * H5
   */
  H5("h5"),
  /**
   * 微信小程序
   */
  WECHAT("wechat"),
  /**
   * 主机
   */
  HOST("host");

  private String type;

  public String getType() {
    return type;
  }

  PlatformEnum(String type) {
    this.type = type;
  }
}
