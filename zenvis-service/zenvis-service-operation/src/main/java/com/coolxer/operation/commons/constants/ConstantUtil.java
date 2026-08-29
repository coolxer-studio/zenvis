package com.coolxer.operation.commons.constants;

/**
 * @author yaoqi.li
 */
public class ConstantUtil {
  /**
   * RatingData在redis中存储的key的格式:rating-data-[appId]:[guid]
   */
  public static final String RATING_DATA_REDIS_KEY_FORMAT = "rating-data-%s:%s";

  /**
   * RatingData在redis中存储的key的HashValue对应的最新一次启动数据的key
   */
  public static final String LAST_START_RATING_DATA_REDIS_VALUE_HASH_KEY = "last-start";

  /**
   * RatingScore在redis中存储的key的格式:rating-score-[appId]:[ratingRuleCode]
   */
  public static final String RATING_SCORE_REDIS_KEY_FORMAT = "rating-score-%s:%s";

  /**
   * RatingScore允许最大分值
   */
  public static final int RATING_SCORE_MAX_SCORE = 99999;

  /**
   * RatingData在生成分区key用的分隔符号
   */
  public static final String RATING_DATA_PARTITION_KEY_SPLIT = "|";
}
