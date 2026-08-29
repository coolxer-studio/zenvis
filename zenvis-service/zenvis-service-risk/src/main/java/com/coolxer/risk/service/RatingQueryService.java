package com.coolxer.risk.service;

import com.coolxer.risk.model.RatingData;
import com.coolxer.risk.model.RatingScore;

import java.util.Date;

/**
 * @author yaoqi.li
 */
public interface RatingQueryService {

  /**
   * 获取guid分数
   *
   * @param guid       全局唯一id
   * @param appId      appId
   * @param ratingCode 策略编码
   * @return 返回体
   */
  RatingScore getRatingScore(String guid, String appId, String ratingCode);

  /**
   * 获取guid的标签
   *
   * @param guid      全局唯一id
   * @param appId     appId
   * @param startTime 开始时间
   * @param endTime   结束时间
   * @return 返回结果类
   */
  RatingData getRatingData(String guid, String appId, Date startTime, Date endTime);

  /**
   * 获取guid的标签
   *
   * @param guid    全局唯一id
   * @param appId   appId
   * @param startId 开始id
   * @return 返回结果类
   */
  RatingData getRatingData(String guid, String appId, String startId);


}
