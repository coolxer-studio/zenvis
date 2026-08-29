package com.coolxer.asset.commons.enums;


/**
 * @author yaoqi.li
 */

public enum MsgTypeEnum {
  /**
   * device
   */
  START("start"),
  /**
   * device
   */
  DEVICE("device"),
  /**
   * app
   */
  APP("app");

  private String name;

  public String getName() {
    return name;
  }

  MsgTypeEnum(String name) {
    this.name = name;
  }
}
