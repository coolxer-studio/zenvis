package com.coolxer.risk.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * @author yaoqi.li
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FactMsg {
  private Fact fact;
  private List<Agenda> agendas;
  private Date serverTime;

  /**
   * 转化为RatingData对象
   *
   * @return
   */
  public RatingData toRatingData() {
    if (Objects.isNull(this.fact) || Objects.isNull(this.fact.common) || Objects.isNull(this.agendas) || Objects.isNull(this.serverTime)) {
      return null;
    }
    HashMap<String, Integer> tagMap = new HashMap<>(8);
    for (Agenda agenda : this.agendas) {
      tagMap.put(agenda.generateTagMapKey(), tagMap.getOrDefault(agenda.getTag(), 0) + 1);
    }
    return new RatingData(fact.common.getGuid(), fact.common.getAppId(), fact.common.getStartId(), this.serverTime, tagMap);
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Fact {
    private Common common;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Common {
    private String userId;
    private String guid;
    private String startId;
    private String sdkVersion;
    private String appId;
    private String appName;
    private String appPackage;
    private String appVersion;
    private String platform;
    private String manufacturer;
    private String model;
    private String system;
    private String systemVersion;
    private String netType;
    private String lanIp;
    private String wanIp;
    private double latitude;
    private double longitude;
    private String country;
    private String province;
    private String city;
    private String county;
    private String thoroughfare;
    private String clientTime;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Agenda {
    private String tag;
    private String source;
    private String level;

    public String generateTagMapKey(){
      return String.format("%s:%s", this.tag, this.level);
    }
  }
}

