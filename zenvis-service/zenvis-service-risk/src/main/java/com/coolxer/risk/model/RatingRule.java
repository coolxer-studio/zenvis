package com.coolxer.risk.model;

import com.coolxer.risk.commons.constants.ConstantUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * @author yaoqi.li
 */
@Data
@ToString
@NoArgsConstructor
public class RatingRule {

  /**
   * 策略名称
   */
  private String name;

  /**
   * 策略编码
   */
  private String code;

  /**
   * 应用id
   */
  private String appId;

  /**
   * 计算周期
   */
  private Integer computationPeriod;

  /**
   * 风险等级规则 格式（json）: {"低风险":{"from":0,"to":20},"中风险":{"from":20,"to":40}...}
   */
  private HashMap<String, GradeRule> gradeRules;

  /**
   * 状态 1启用 0停用
   */
  private Integer status;

  /**
   * 分值计算细则
   */
  private List<ScoreRule> scoreRules = new ArrayList<>();

  /**
   * 生成唯一的key(在redis中使用)
   * @return
   */
  public String spitRedisKey() {
    return String.format(ConstantUtil.RATING_SCORE_REDIS_KEY_FORMAT, appId, code);
  }

  @Data
  public static class GradeRule {
    /**
     * 等级对一个的左区间分值
     */
    private Integer from;
    /**
     * 等级对一个的右区间分值
     */
    private Integer to;

    public boolean isHit(int score) {
      //如果from为空，则赋值为最小整数
      if (from == null) {
        from = Integer.MIN_VALUE;
      }
      //如果to为空，则赋值为最大整数
      if (to == null) {
        to = Integer.MAX_VALUE;
      }
      //返回score是否在from和to之间
      return score >= from && score <= to;
    }
  }

  @Data
  @NoArgsConstructor
  public static class ScoreRule {

    /**
     * 设备标签
     */
    private String tag;

    /**
     * 基础分
     */
    private Integer basicScore;

    /**
     * 叠加分
     */
    private Integer superpositionScore;

    /**
     * 封顶分
     */
    private Integer topScore;


  }

}
