package com.coolxer.risk.component;

import com.coolxer.risk.configuration.ApplicationConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动加载类
 *
 * @author yaoqi.li
 * @date 2023/7/3 10:47
 */
@Component
@Slf4j
@Order(value = 1)
public class StartRunnerComponent implements CommandLineRunner {

  @Autowired
  private ApplicationConfig applicationConfig;

  @Override
  public void run(String... args) throws Exception {
    log.info("StartRunnerComponent is run");
    log.info("totalMemory:{}M", Runtime.getRuntime().totalMemory() / 1024 / 1024);
    String userDir = System.getProperty("user.dir");
    log.info("userDir is {}", userDir);

    // TODO 后续需要支持监听文件变化自动加载配置文件
    // 加载配置文件
    applicationConfig.loadRatingRuleConfig();

    log.info("Service start up");
    Runtime.getRuntime().addShutdownHook(new Thread(() -> log.error("Service shutdown")));
  }


}
