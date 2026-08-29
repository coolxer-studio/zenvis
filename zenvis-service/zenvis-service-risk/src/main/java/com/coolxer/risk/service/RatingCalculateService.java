package com.coolxer.risk.service;

import com.coolxer.risk.model.RatingData;

import java.util.List;
import java.util.Map;

/**
 * 威胁指数数据表处理接口
 *
 * @author yaoqi.li
 */
public interface RatingCalculateService {

  /**
   * 实时更新最近一次启动的数据
   *
   * @param groupedRatingDataList
   */
  void updateLastStart(Map<String, List<RatingData>> groupedRatingDataList);

  /**
   * 实时评分
   *
   * @param groupedRatingDataList
   */
  void rating(Map<String, List<RatingData>> groupedRatingDataList);

  /**
   * 整体重新评分（建议定时任务调用）
   */
  void reRating();

}
