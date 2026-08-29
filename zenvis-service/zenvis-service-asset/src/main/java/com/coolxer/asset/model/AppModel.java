package com.coolxer.asset.model;

import com.coolxer.asset.commons.enums.PlatformEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


/**
 * APP模型
 *
 * @author yaoqi.li
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppModel {

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
   * 应用列表详情（jsonString）
   */
  private String appInfo;


}
