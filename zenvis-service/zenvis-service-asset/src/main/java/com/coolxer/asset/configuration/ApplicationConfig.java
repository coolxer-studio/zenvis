package com.coolxer.asset.configuration;

import com.coolxer.asset.model.DeviceIdRule;
import com.coolxer.asset.utils.JacksonUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;

import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Paths.get;

/**
 * @author yaoqi.li
 */
@Slf4j
@Getter
@Configuration
public class ApplicationConfig {

  @Value("${device.id.rule.config.path}")
  private String deviceIdRuleConfigPath;


  /**
   * 策略列表。
   */
  public static DeviceIdRule deviceIdRule = null;

  public void loadDeviceIdRuleConfig(){
    try {
      String deviceIdRuleConfigContent = new String(readAllBytes(get(deviceIdRuleConfigPath)));
      deviceIdRule = JacksonUtil.toObject(deviceIdRuleConfigContent, DeviceIdRule.class);
    } catch (IOException e) {
      log.error("loadDeviceIdRuleConfig Error[{}]", deviceIdRuleConfigPath,e);
    }
  }
}
