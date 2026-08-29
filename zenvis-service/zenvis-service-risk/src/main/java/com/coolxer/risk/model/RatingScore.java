package com.coolxer.risk.model;

import com.coolxer.risk.commons.constants.ConstantUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备威胁指数数据类
 *
 * @author yaoqi.li
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingScore {

  /**
   * 全局唯一id（与redis中的key转化）
   */
  private String guid;

  /**
   * 应用id（与redis中的key转化）
   */
  private String appId;

  /**
   * 评级编码（与redis中的key转化）
   */
  private String ratingCode;

  /**
   * 评分结果（与redis中的value转化）
   */
  private Integer score;

  /**
   * 评分等级（每次根据规则（RatingRule.gradeRules）把评分（score）转化为对应的等级（grade））
   */
  private String grade;

  /**
   * 生成唯一的key(在redis中使用)
   * @return
   */
  public String spitRedisKey() {
    return String.format(ConstantUtil.RATING_SCORE_REDIS_KEY_FORMAT, appId, ratingCode);
  }

}
