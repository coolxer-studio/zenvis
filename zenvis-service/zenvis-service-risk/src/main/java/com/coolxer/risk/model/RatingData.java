package com.coolxer.risk.model;

import com.coolxer.risk.commons.constants.ConstantUtil;
import com.coolxer.risk.model.vo.Label;
import com.coolxer.risk.utils.DateUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.text.ParseException;
import java.util.*;

/**
 * 设备威胁指数数据类
 *
 * @author yaoqi.li
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingData {

  /**
   * 全局唯一id（与redis中的key转化）
   */
  private String guid;

  /**
   * 应用id（与redis中的key转化）
   */
  private String appId;

  /**
   * 启动id（用于保存最近一次启动的标记信息使用，redis中的value包含）
   */
  private String startId;

  /**
   * 消息时间（入库时候作为redis的hashKey的查询条件使用，数据合并后出库只能定义为最新数据对应的时间）
   */
  private Date msgTime;

  /**
   * 标签的map集合，key：标签 value：标签出现的次数
   */
  private Map<String, Integer> tagMap;

  /**
   * 生成启动唯一的key
   * @return
   */
  public String spitStartPartitionKey() {
    return StringUtils.joinWith(ConstantUtil.RATING_DATA_PARTITION_KEY_SPLIT,startId, guid, appId);
  }

  /**
   * 根据startPartitionKey 解析出startId、guid、appId
   * @param startPartitionKey
   */
  public RatingData eatStartPartitionKey(String startPartitionKey) {
    String[] startPartitionKeyArr = StringUtils.split(startPartitionKey,ConstantUtil.RATING_DATA_PARTITION_KEY_SPLIT);
    this.startId = startPartitionKeyArr[0];
    this.guid = startPartitionKeyArr[1];
    this.appId = startPartitionKeyArr[2];
    return this;
  }

  /**
   * 生成当天唯一的key(在内存中使用)
   * @return
   */
  public String spitTimePartitionKey() {
    return StringUtils.joinWith(ConstantUtil.RATING_DATA_PARTITION_KEY_SPLIT,DateUtil.SIMPLE_DATE_FORMAT_YYYY_MM_DD_01.format(msgTime), guid, appId);
  }

  /**
   * 根据timePartitionKey 解析出msgTime,guid、appId
   * @param timePartitionKey
   */
  public RatingData eatTimePartitionKey(String timePartitionKey) {
    String[] timePartitionKeyArr = StringUtils.split(timePartitionKey,ConstantUtil.RATING_DATA_PARTITION_KEY_SPLIT);
    try {
      this.msgTime = DateUtil.SIMPLE_DATE_FORMAT_YYYY_MM_DD_01.parse(timePartitionKeyArr[0]);
    } catch (ParseException e) {
      e.printStackTrace();
    }
    this.guid = timePartitionKeyArr[1];
    this.appId = timePartitionKeyArr[2];
    return this;
  }

  /**
   * 生成当天唯一的key(在redis中使用)
   * @return
   */
  public String spitRedisKey() {
    return String.format(ConstantUtil.RATING_DATA_REDIS_KEY_FORMAT, appId, guid);
  }


  /**
   * 结果合并
   *
   * @param sourceTagMap 数据表中的数据，key：datatype，value:个数
   */
  public void mergeTagMap(Map<String, Integer> sourceTagMap) {
    if(Objects.isNull(this.tagMap)){
      this.tagMap = new HashMap<>();
    }
    if (Objects.nonNull(sourceTagMap)) {
      for (Map.Entry<String, Integer> entry : sourceTagMap.entrySet()) {
        this.tagMap.put(entry.getKey(), entry.getValue() + this.tagMap.getOrDefault(entry.getKey(), 0));
      }
    }
  }

  /**
   * 结果合并
   * @param sourceTagMapList
   */
  public void mergeTagMapList(List<Map<String, Integer>> sourceTagMapList) {
    if (Objects.nonNull(sourceTagMapList)) {
      sourceTagMapList.stream().forEach(this::mergeTagMap);
    }
  }

  /**
   * 结果合并
   * @param sourceRatingData
   */
  public void mergeRatingData(RatingData sourceRatingData) {
    this.setGuid(sourceRatingData.getGuid());
    this.setAppId(sourceRatingData.getAppId());
    this.setMsgTime(sourceRatingData.getMsgTime());
    this.setStartId(sourceRatingData.getStartId());
    this.mergeTagMap(sourceRatingData.getTagMap());
  }

  /**
   * 结果合并
   * @param sourceRatingDataList
   */
  public void mergeRatingDataList(List<RatingData> sourceRatingDataList) {
    if (sourceRatingDataList.size() > 0) {
      RatingData lastRatingData = sourceRatingDataList.get(sourceRatingDataList.size() - 1);
      this.setGuid(lastRatingData.getGuid());
      this.setAppId(lastRatingData.getAppId());
      this.setMsgTime(lastRatingData.getMsgTime());
      this.setStartId(lastRatingData.getStartId());
      for (RatingData sourceRatingData : sourceRatingDataList) {
        this.mergeTagMap(sourceRatingData.getTagMap());
      }
    }
  }

  public List<Label> toLabel() {
    Map<String,List<String>> labelMap = new HashMap<>();
    if (Objects.nonNull(this.getTagMap())) {
      this.getTagMap().forEach((k, v) -> {
        String label = StringUtils.substringBeforeLast(k,":");
        String level = StringUtils.substringAfterLast(k,":").toLowerCase();
        List<String> valueList = labelMap.getOrDefault(level,new ArrayList<>());
        valueList.add(String.format("%s(%d)", label, v));
        labelMap.put(level,valueList);
      });
    }
    List<Label> labelList = new ArrayList<>();
    labelMap.forEach((k, v) ->{
      labelList.add(new Label(k,v));
    });
    return labelList;
  }
}
