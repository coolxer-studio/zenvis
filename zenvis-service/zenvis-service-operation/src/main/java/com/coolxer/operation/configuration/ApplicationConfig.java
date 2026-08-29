package com.coolxer.operation.configuration;

import com.coolxer.operation.utils.JacksonUtil;
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

  @Value("${operation.rule.config.path}")
  private String operationRuleConfigPath;

//  /**
//   * 威胁指数策略列表。
//   */
//  public static List<RatingRule> RATING_RULE_LIST = new ArrayList<>();
//
//
//  public void loadRatingRuleConfig(){
//    try {
//      String ratingDataConfigContent = new String(readAllBytes(get(operationRuleConfigPath)));
//      List<RatingRule> ratingRuleList = JacksonUtil.toList(ratingDataConfigContent, new TypeReference<>() {});
//      RATING_RULE_LIST = ratingRuleList;
//    } catch (IOException e) {
//      log.error("loadRatingRuleConfig Error[{}]", operationRuleConfigPath,e);
//    }
//  }

}
