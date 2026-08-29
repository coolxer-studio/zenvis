package com.coolxer.operation.controller;


import com.coolxer.operation.model.vo.Label;
import com.coolxer.operation.model.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;

/**
 * 查询接口
 *
 * @author yaoqi.li
 * @date 2023/6/14 19:34
 */
@Api
@Slf4j
@RestController
@RequestMapping("/operation_index")
public class OperationIndexController {

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
    return Result.success(null);
  }


}
