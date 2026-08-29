package com.coolxer.risk.schedule;

import com.coolxer.risk.service.RatingCalculateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/**
 * 定时任务更新威胁指数
 *
 * @author yaoqi.li
 */
@Slf4j
@Component
@EnableScheduling
public class ScheduleTask {

  @Autowired
  private RatingCalculateService ratingCalculateService;

  /**
   * 过期一天后需要更新评分表中的所有评分.
   */
  @Scheduled(cron = "0 0 1 * * *")
  public void updateThreatIndex() {
    ratingCalculateService.reRating();
  }

}

