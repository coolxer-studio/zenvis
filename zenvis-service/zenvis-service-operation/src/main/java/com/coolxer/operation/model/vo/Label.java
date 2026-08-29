package com.coolxer.operation.model.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

/**
 * @author yaoqi.li
 */
@Data
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Label{
  private String type;
  private List<String> valueList;
}
