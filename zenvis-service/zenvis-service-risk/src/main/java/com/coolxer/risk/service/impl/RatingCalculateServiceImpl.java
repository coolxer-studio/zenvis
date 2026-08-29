package com.coolxer.risk.service.impl;

import com.coolxer.risk.model.RatingData;
import com.coolxer.risk.model.RatingRule;
import com.coolxer.risk.model.RatingScore;
import com.coolxer.risk.service.RatingCalculateService;
import com.coolxer.risk.configuration.ApplicationConfig;
import com.coolxer.risk.utils.CommonUtil;
import com.coolxer.risk.commons.constants.ConstantUtil;
import com.coolxer.risk.utils.DateUtil;
import com.coolxer.risk.utils.JacksonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * redis数据表处理类
 *
 * @author: yaoqi.li
 */
@Slf4j
@Service
public class RatingCalculateServiceImpl implements RatingCalculateService {

  @Autowired
  private ApplicationConfig applicationConfig;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public void updateLastStart(Map<String, List<RatingData>> groupedRatingDataList) {
    if (groupedRatingDataList.isEmpty()) {
      return;
    }
    try {
      // TODO 需要使用事务+watch解决并发一致性问题
      for (Map.Entry<String, List<RatingData>> oneStartRatingData : groupedRatingDataList.entrySet()) {
        String redisKey = new RatingData().eatStartPartitionKey(oneStartRatingData.getKey()).spitRedisKey();
        // 创建空对象用来承载数据
        RatingData ratingData = new RatingData();
        // 合并本批次数据
        ratingData.mergeRatingDataList(oneStartRatingData.getValue());
        // 如果本批次没有有效数据就不需要继续了
        if (Objects.isNull(ratingData) || ratingData.getTagMap().size() == 0) {
          continue;
        }
        // 合并当天的历史数据
        String lastStartRatingDataInRedis = (String) stringRedisTemplate
            .opsForHash()
            .get(redisKey, ConstantUtil.LAST_START_RATING_DATA_REDIS_VALUE_HASH_KEY);
        if (Strings.isNotBlank(lastStartRatingDataInRedis)) {
          RatingData lastStartRatingData = null;
          try {
            lastStartRatingData = JacksonUtil.toObject(lastStartRatingDataInRedis, RatingData.class);
          } catch (JsonProcessingException e) {
            e.printStackTrace();
          }
          // 如果startId和当前是一样的，需要将实时数据与redis数据结果合并
          if (ratingData.getStartId().equals(lastStartRatingData.getStartId())) {
            ratingData.mergeRatingData(lastStartRatingData);
          }
        }
        // 更新redis
        String redisValue = null;
        try {
          redisValue = JacksonUtil.toJson(ratingData);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        stringRedisTemplate
            .opsForHash()
            .put(
                redisKey,
                ConstantUtil.LAST_START_RATING_DATA_REDIS_VALUE_HASH_KEY,
                redisValue
            );
      }
    } catch (Exception e) {
      log.error("process rating data error", e);
    }
  }

  @Override
  public void rating(Map<String, List<RatingData>> groupedRatingDataList) {
    if (groupedRatingDataList.isEmpty()) {
      return;
    }
    try {
      // 更新数据表
      List<RatingData> summarizedRatingDataList = updateRatingData(groupedRatingDataList);
      // 计算分数
      calculateScoreAndSave(summarizedRatingDataList, ApplicationConfig.RATING_RULE_LIST);
    } catch (Exception e) {
      log.error("process rating data error", e);
    }
  }

  /**
   * 更新威胁指数数据表
   *
   * @param groupedRatingData 分组数据
   */
  public List<RatingData> updateRatingData(Map<String, List<RatingData>> groupedRatingData) {
    // TODO 需要使用事务+watch解决并发一致性问题
    List<RatingData> summarizedRatingDataList = new ArrayList<>();
    for (Map.Entry<String, List<RatingData>> oneDeviceRatingData : groupedRatingData.entrySet()) {
      RatingData queryRatingData = new RatingData().eatTimePartitionKey(oneDeviceRatingData.getKey());
      String redisHashKey = DateUtil.SIMPLE_DATE_FORMAT_YYYY_MM_DD_01.format(queryRatingData.getMsgTime());
      String redisKey = queryRatingData.spitRedisKey();

      // 创建空对象用来承载数据
      RatingData ratingData = new RatingData();
      // 合并本批次数据
      ratingData.mergeRatingDataList(oneDeviceRatingData.getValue());
      // 如果本批次没有有效数据就不需要继续了
      if (Objects.isNull(ratingData) || ratingData.getTagMap().size() == 0) {
        continue;
      }
      // 合并当天的历史数据
      String todayRatingDataInRedis = (String) stringRedisTemplate
          .opsForHash()
          .get(redisKey, redisHashKey);
      if (Strings.isNotBlank(todayRatingDataInRedis)) {
        HashMap<String, Integer> redisTagMap = null;
        try {
          redisTagMap = (HashMap<String, Integer>) JacksonUtil.toMap(todayRatingDataInRedis, new TypeReference<Map<String, Integer>>() {
          });
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        // 将实时数据与redis的当天数据结果合并
        ratingData.mergeTagMap(redisTagMap);
      }
      // 更新redis
      String redisValue = null;
      try {
        redisValue = JacksonUtil.toJson(ratingData.getTagMap());
      } catch (JsonProcessingException e) {
        e.printStackTrace();
      }
      stringRedisTemplate
          .opsForHash()
          .put(
              redisKey,
              redisHashKey,
              redisValue
          );
      stringRedisTemplate
          .expire(
              redisKey,
              applicationConfig.getDataTableExpire(),
              TimeUnit.DAYS
          );
      summarizedRatingDataList.add(ratingData);
    }
    return summarizedRatingDataList;
  }

  /**
   * 按照策略批量查询数据表并更新评分表
   *
   * @param appId         应用id
   * @param guidList      一个批次的guid集合
   * @param byStartAndEnd 时间
   * @param ratingRule    策略
   */
  public void getRatingDataByGuidAndUpdateScore(String appId, List<String> guidList, List<String> byStartAndEnd, RatingRule ratingRule) {

    for (String guid : guidList) {
      RatingData ratingData = new RatingData(guid,appId,null,null,null);

      List<Object> multiGet = stringRedisTemplate.opsForHash()
          .multiGet(ratingData.spitRedisKey(), Collections.singleton(byStartAndEnd));
      List<Map<String, Integer>> redisStoreRecords = multiGet.stream()
          .filter(Objects::nonNull)
          .map(x -> {
            try {
              return (HashMap<String, Integer>) JacksonUtil.toMap((String) x, new TypeReference<Map<String, Integer>>() {
              });
            } catch (JsonProcessingException e) {
              e.printStackTrace();
            }
            return null;
          })
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
      // 如果再策略周期内没有查到数据，则变为安全设备，删除评分
      if (redisStoreRecords.isEmpty()) {
        deleteRedisRatingScore(ratingRule, guid);
        continue;
      }
      // 时间为最后有数据的时间

      ratingData.mergeTagMapList(redisStoreRecords);

      Integer score = calculateScore(ratingData, ratingRule);
      // 更新评分
      saveRatingScoreToRedis(ratingRule, guid, score);
    }

  }

  /**
   * 根据策略查询周期内的数据表
   *
   * @param ratingRule       威胁指数策略
   * @param sourceRatingData 当天的威胁数据对象
   * @return 威胁数据对象
   */
  private RatingData aggregateRatingDataWithRule(RatingRule ratingRule, RatingData sourceRatingData) {

    RatingData ratingData = new RatingData(sourceRatingData.getGuid(),sourceRatingData.getAppId(), sourceRatingData.getStartId(), sourceRatingData.getMsgTime(),null);

    Integer computationPeriod = ratingRule.getComputationPeriod();
    // 计算周期结束时间
    long currentTimeMillis = System.currentTimeMillis();
    long startTime = DateUtil.getCalculationPeriodStartTime(computationPeriod, currentTimeMillis);
    List<Object> daysByStartAndEnd = Arrays.asList(DateUtil.getDaysByStartAndEnd(startTime, currentTimeMillis).toArray());
    // 获取需要查询的数据天数集合,查询redis中的数据
    List<Map<String, Integer>> ratingDataTagMapListByPeriod = stringRedisTemplate.opsForHash().multiGet(
            ratingData.spitRedisKey(),
            daysByStartAndEnd)
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

  /**
   * 计算威胁指数分值
   *
   * @param ratingDataList 今日汇总数据
   * @param ratingRuleList 威胁指数策略列表
   */
  private void calculateScoreAndSave(List<RatingData> ratingDataList, List<RatingRule> ratingRuleList) {

    for (RatingData ratingData : ratingDataList) {
      // 获取适用的策略列表
      List<RatingRule> hitRatingRuleList = ratingRuleList.stream()
          .filter(ratingRule -> StringUtils.equals(ratingData.getAppId(), ratingRule.getAppId()))
          .collect(Collectors.toList());

      for (RatingRule ratingRule : hitRatingRuleList) {
        // 获取策略下查询数据表得到的威胁汇总数据类
        RatingData aggregateRatingData = aggregateRatingDataWithRule(ratingRule, ratingData);
        // 算分
        int score = calculateScore(aggregateRatingData, ratingRule);
        saveRatingScoreToRedis(
            ratingRule,
            ratingData.getGuid(),
            score
        );
      }
    }

  }

  /**
   * 将威胁指数分数存入redis评分表
   *
   * @param ratingRule             策略
   * @param guid                   设备id
   * @param score 分数
   */
  private void saveRatingScoreToRedis(RatingRule ratingRule, String guid, Integer score) {

    if (score == 0) {
      return;
    }
    // 最大分
    if (score > ConstantUtil.RATING_SCORE_MAX_SCORE) {
      score = ConstantUtil.RATING_SCORE_MAX_SCORE;
    }

    String ratingScoreRedisKey = new RatingScore(guid,ratingRule.getAppId(),ratingRule.getCode(),null,null).spitRedisKey();
    stringRedisTemplate
        .opsForZSet()
        .add(ratingScoreRedisKey, guid, score);
    // 设置过期时间为数据计算周期时长
    stringRedisTemplate.expire(
        ratingScoreRedisKey,
        ratingRule.getComputationPeriod(),
        TimeUnit.DAYS
    );
  }

  /**
   * 删除0分的设备评分数据
   *
   * @param ratingRule 威胁指数策略
   * @param guid       guid
   */
  private void deleteRedisRatingScore(RatingRule ratingRule, String guid) {
    String redisKey = new RatingScore(guid,ratingRule.getAppId(),ratingRule.getCode(),null,null).spitRedisKey();
    stringRedisTemplate.opsForZSet().remove(redisKey, guid);
  }


  /**
   * 算分
   *
   * @param ratingData 策略查询出来的汇总数据结果
   * @param ratingRule 策略
   * @return 分值
   */
  private int calculateScore(RatingData ratingData, RatingRule ratingRule) {
    int totalScore = 0;
    // 获取分值策略
    List<RatingRule.ScoreRule> enableScoreRule = ratingRule.getScoreRules();
    for (RatingRule.ScoreRule scoreRule : enableScoreRule) {
      String tag = scoreRule.getTag();
      // tag数量
      int tagCount = ratingData.getTagMap().getOrDefault(tag,0);
      // 基础分：发生一次，记录风险分值=基础分
      // 叠加分：每多发生一次，风险分值增加一次叠加分
      int score = tagCount > 0 ? scoreRule.getBasicScore() + ((tagCount - 1) * scoreRule.getSuperpositionScore()) : 0;
      // 当单个威胁/风险/安全事件的风险分值超过封顶分时，风险分值按照封顶分统计
      score = score > scoreRule.getTopScore() ? scoreRule.getTopScore() : score;
      // 累计本次分值
      totalScore += score;
    }
    return totalScore;
  }

  @Override
  public void reRating() {
    log.info("开始矫正所有设备威胁指数任务...");

    long currentTimeMillis = System.currentTimeMillis();
    for (RatingRule ratingRule : ApplicationConfig.RATING_RULE_LIST) {
      String appId = ratingRule.getAppId();
      //需要查询数据表的天数
      Integer computationPeriod = ratingRule.getComputationPeriod();
      long startTime = DateUtil.getCalculationPeriodStartTime(computationPeriod, currentTimeMillis);
      List<String> byStartAndEnd = DateUtil.getDaysByStartAndEnd(startTime, currentTimeMillis);

      List<String> allGuidByRuleCode = getAllGuidByRuleCode(ratingRule);
      //对该策略下所有的guid进行分批再查询数据表计算分数，避免整体查询计算时间过长，这时候实时处理任务正在进行，批量任务可能会覆盖结果导致误差
      List<List<String>> splitList = CommonUtil.splitList(allGuidByRuleCode, 1000);
      for (List<String> list : splitList) {
        getRatingDataByGuidAndUpdateScore(appId, list, byStartAndEnd, ratingRule);
      }
    }

    log.info("矫正所有设备威胁指数任务完成，用时{}ms", System.currentTimeMillis() - currentTimeMillis);
  }

  /**
   * 从redis中获取指定策略的所有guid
   *
   * @return 所有guid的集合
   */
  private List<String> getAllGuidByRuleCode(RatingRule ratingRule) {
    Set<String> guidSet = new HashSet<>();
    Long guidCount = stringRedisTemplate.opsForZSet().zCard(ratingRule.spitRedisKey());
    if (guidCount != null && guidCount > 0) {
      guidSet = stringRedisTemplate.opsForZSet().range(ratingRule.spitRedisKey(), 0, guidCount);
    }
    return new ArrayList<>(guidSet);
  }


}
