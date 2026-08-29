package com.coolxer.risk.service.impl;

import com.coolxer.risk.commons.exception.ApiException;
import com.coolxer.risk.commons.enums.ResultCodeEnum;
import com.coolxer.risk.model.RatingData;
import com.coolxer.risk.model.RatingRule;
import com.coolxer.risk.model.RatingScore;
import com.coolxer.risk.service.RatingQueryService;
import com.coolxer.risk.configuration.ApplicationConfig;
import com.coolxer.risk.commons.constants.ConstantUtil;
import com.coolxer.risk.utils.DateUtil;
import com.coolxer.risk.utils.JacksonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 评分指数查询服务类
 *
 * @author: yaoqi.li
 */
@Slf4j
@Service
public class RatingQueryServiceImpl implements RatingQueryService {

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public RatingScore getRatingScore(String guid, String appId, String ratingCode) {
    RatingScore ratingScore = new RatingScore(guid, appId, ratingCode,null,null);
    //根据编码和app_id查询到策略ID
    List<RatingRule> ratingRuleList = ApplicationConfig.RATING_RULE_LIST.stream()
        .filter(ratingRule -> ratingRule.getCode().equals(ratingCode) && ratingRule.getAppId().equals(appId))
        .collect(Collectors.toList());
    // 没有查询到策略，直接结束
    if (ratingRuleList.isEmpty()) {
      throw new ApiException(ResultCodeEnum.UNKNOWN_RATING_RULE);
    }
    // 取结果中的第一个作为查询使用的策略
    RatingRule ratingRule = ratingRuleList.get(0);
    Double resultScore = stringRedisTemplate.opsForZSet().score(
        ratingScore.spitRedisKey(), guid);
    int score = Objects.isNull(resultScore)? 0: resultScore.intValue();
    ratingScore.setScore(score);
    //根据分数获取风险等级
    String grade = null;
    for (Map.Entry<String, RatingRule.GradeRule> entry : ratingRule.getGradeRules().entrySet()) {
      if (entry.getValue().isHit(score)) {
        grade = entry.getKey();
        break;
      }
    }
    ratingScore.setGrade(grade);
    // 返回结果
    return ratingScore;
  }

  @Override
  public RatingData getRatingData(String guid, String appId, String startId) {
    // 生成redis查询需要的key：最近启动的数据是个固定的key
    List<Object> ratingDataHashKeys = Arrays.asList(new String[]{ConstantUtil.LAST_START_RATING_DATA_REDIS_VALUE_HASH_KEY});
    // 查询redis中的数据
    List<RatingData> ratingDataTagMapListByPeriod = stringRedisTemplate.opsForHash().multiGet(
            new RatingData(guid,appId,startId,null,null).spitRedisKey(), ratingDataHashKeys)
        .stream()
        .filter(Objects::nonNull)
        .map(object -> {
          try {
            return JacksonUtil.toObject((String) object, RatingData.class);
          } catch (JsonProcessingException e) {
            e.printStackTrace();
          }
          return null;
        })
        .filter(ratingData -> Objects.nonNull(ratingData) && ratingData.getStartId().equals(startId))
        .collect(Collectors.toList());

    // 合并结果
    if (ratingDataTagMapListByPeriod.size() > 0) {
      return ratingDataTagMapListByPeriod.get(0);
    }
    return null;
  }

  @Override
  public RatingData getRatingData(String guid, String appId, Date startTime, Date endTime) {
    // 生成redis查询需要的key：是个日期列表
    List<Object> ratingDataHashKeys = Arrays.asList(DateUtil.getDaysByStartAndEnd(startTime.getTime(), endTime.getTime()).toArray());
    // 构造RatingData用来接收数据
    RatingData ratingData = new RatingData(guid,appId,null,endTime,null);

    // 获取需要查询的数据天数集合,查询redis中的数据
    List<Map<String, Integer>> ratingDataTagMapListByPeriod = stringRedisTemplate.opsForHash().multiGet(
            ratingData.spitRedisKey(),
            ratingDataHashKeys)
        .stream()
        .filter(Objects::nonNull)
        .map(object -> {
          try {
            return (HashMap<String, Integer>) JacksonUtil.toMap((String) object, new TypeReference<Map<String, Integer>>() {
            });
          } catch (JsonProcessingException e) {
            e.printStackTrace();
          }
          return new HashMap<String, Integer>();
        })
        .collect(Collectors.toList());

    // 合并结果
    ratingData.mergeTagMapList(ratingDataTagMapListByPeriod);
    return ratingData;
  }


}
