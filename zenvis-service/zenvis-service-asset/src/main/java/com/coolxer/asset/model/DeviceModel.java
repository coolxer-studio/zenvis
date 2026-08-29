package com.coolxer.asset.model;

import com.coolxer.asset.commons.enums.PlatformEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


/**
 * 设备模型（各个平台的设备信息转化而来）
 *
 * @author yaoqi.li
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceModel {

  /**
   * 相似度阈值
   */
  private int rate;

  /**
   * 全局唯一id
   */
  private String guid;

  /**
   * 应用id
   */
  private String appId;

  /**
   * 平台
   */
  private PlatformEnum platform;

  /**
   * 汇聚需要用的key
   */
  private List<String> unionKeys;

  /**
   * 设备详情（jsonString）
   */
  private String deviceInfo;


}
