package com.coolxer.operation.commons.enums;


/**
 * @author yaoqi.li
 */

public enum MsgTypeEnum {
  /**
   * device
   */
  START("start"),
  /**
   * activity
   */
  ACTIVITY("activity"),
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
