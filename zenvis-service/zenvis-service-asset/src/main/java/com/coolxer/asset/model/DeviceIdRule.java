package com.coolxer.asset.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 设备id计算规则
 *
 * @author yaoqi.li
 *
 */

@Data
@NoArgsConstructor
public class DeviceIdRule {

  private Rule android;

  private Rule ios;

  private Rule h5;

  private Rule wechat;

  private Rule host;

  @Data
  @NoArgsConstructor
  public static class Rule{
    /**
     * 参与计算的指标集合，内容为class的field，通过反射获取属性值使用
     */
    private List<String> keyList;
    /**
     * 相似度阈值
     */
    private int rate;
  }
}
