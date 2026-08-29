package com.coolxer.risk.configuration;

import com.coolxer.risk.model.RatingRule;
import com.coolxer.risk.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Paths.get;

/**
 * @author yaoqi.li
 */
@Slf4j
@Getter
@Configuration
public class ApplicationConfig {

  @Value("${rating.rule.config.path}")
  private String ratingRuleConfigPath;

  /**
   * 数据表保存时间
   */
  @Value("${rating.data.expire.days:30}")
  private int dataTableExpire;

  /**
   * 威胁指数策略列表。
   */
  public static List<RatingRule> RATING_RULE_LIST = new ArrayList<>();


  public void loadRatingRuleConfig(){
    try {
      String ratingDataConfigContent = new String(readAllBytes(get(ratingRuleConfigPath)));
      List<RatingRule> ratingRuleList = JacksonUtil.toList(ratingDataConfigContent, new TypeReference<>() {});
      RATING_RULE_LIST = ratingRuleList;
    } catch (IOException e) {
      log.error("loadRatingRuleConfig Error[{}]",ratingRuleConfigPath,e);
    }
  }
}
