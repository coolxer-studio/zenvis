package com.coolxer.risk.controller;


import com.coolxer.risk.commons.enums.ResultCodeEnum;
import com.coolxer.risk.model.RatingData;
import com.coolxer.risk.model.RatingScore;
import com.coolxer.risk.model.vo.Label;
import com.coolxer.risk.model.vo.Result;
import com.coolxer.risk.service.RatingQueryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 查询接口
 *
 * @author yaoqi.li
 * @date 2023/6/14 19:34
 */
@Api
@Slf4j
@RestController
@RequestMapping("/risk_index")
public class RiskIndexController {

  @Autowired
  private RatingQueryService ratingQueryService;

  /**
   * 获取所有设备标签
   *
   * @param guid       全局唯一id(必须)
   * @param appId      应用id（必须）
   * @param startId    启动id（可选）
   * @param startTime  数据范围开始时间（可选）
   * @param endTime    数据范围结束时间（可选）
   * @return RatingData类型数据
   */
  @GetMapping(value = "/label/{guid}")
  @ApiOperation(value = "设备标签", notes = "设备标签")
  public Result<List<Label>> label(@PathVariable(value = "guid") String guid, @RequestParam(value = "app_id") String appId, @RequestParam(required = false, value = "start_id") String startId,
                                   @RequestParam(required = false, value = "start_time") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime, @RequestParam(required = false, value = "end_time") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime) {
    RatingData ratingData = null;
    if (Objects.nonNull(startId)) {
      ratingData = ratingQueryService.getRatingData(guid, appId, startId);
    } else if (Objects.nonNull(startTime) && Objects.nonNull(endTime)) {
      ratingData = ratingQueryService.getRatingData(guid, appId, startTime, endTime);
    } else {
      return Result.fail(ResultCodeEnum.ILLEGAL_PARAMETERS);
    }
    if(Objects.isNull(ratingData)){
      return Result.fail(ResultCodeEnum.INNER_ERROR);
    }
    return Result.success(ratingData.toLabel());
  }

  /**
   * 评分
   *
   * @param guid       全局唯一id
   * @param appId      应用id
   * @param ratingCode 评分策略的id
   * @return RatingScore类型数据
   */
  @GetMapping(value = "/rating/{guid}")
  @ApiOperation(value = "评分", notes = "评分")
  public Result<RatingScore> rating(@PathVariable(value = "guid") String guid, @RequestParam(value = "app_id") String appId, @RequestParam(value = "rating_code") String ratingCode) {
    RatingScore ratingScore = ratingQueryService.getRatingScore(guid, appId, ratingCode);
    return Result.success(ratingScore);
  }


}
