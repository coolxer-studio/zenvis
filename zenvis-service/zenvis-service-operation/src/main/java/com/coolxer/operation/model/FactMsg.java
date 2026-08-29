package com.coolxer.operation.model;

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

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  private static class Fact {
    private Common common;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  private static class Common {
    private String guid;
    private String startId;
    private String appId;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  private static class Agenda {
    private String tag;
    private String source;
    private String level;

    public String generateTagMapKey(){
      return String.format("%s:%s", this.tag, this.level);
    }
  }
}

