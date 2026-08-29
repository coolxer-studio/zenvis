package com.coolxer.asset.controller;


import com.coolxer.asset.commons.enums.ResultCodeEnum;
import com.coolxer.asset.model.vo.Result;
import com.coolxer.asset.service.DeviceIdService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
@RequestMapping("/device")
public class DeviceIdController {

  @Autowired
  private DeviceIdService deviceIdService;


  @GetMapping(value = "/info/{guid}")
  @ApiOperation(value = "设备信息", notes = "设备信息")
  public Result<JsonNode> info(@PathVariable(value = "guid") String guid) {
    if (Objects.nonNull(guid)) {
      return Result.success(deviceIdService.getDeviceInfo(guid));
    } else {
      return Result.fail(ResultCodeEnum.ILLEGAL_PARAMETERS);
    }
  }

  @GetMapping(value = "/app/{guid}")
  @ApiOperation(value = "设备app信息", notes = "设备app信息")
  public Result<JsonNode> app(@PathVariable(value = "guid") String guid) {
    if (Objects.nonNull(guid)) {
      return Result.success(deviceIdService.getDeviceApp(guid));
    } else {
      return Result.fail(ResultCodeEnum.ILLEGAL_PARAMETERS);
    }
  }

  @GetMapping(value = "/id/{guid}")
  @ApiOperation(value = "查询设备指纹", notes = "查询设备指纹")
  public Result<String> uuid(@PathVariable(value = "guid") String guid) {
    String deviceId = deviceIdService.getDeviceId(guid);
    return Result.success(deviceId);
  }


  @GetMapping(value = "/guid/{id}")
  @ApiOperation(value = "查询guid", notes = "查询guid")
  public Result<String[]> deviceId(@PathVariable(value = "id") String id) {
    String[] guidArray = deviceIdService.getGuidArray(id);
    return Result.success(guidArray);
  }

}
