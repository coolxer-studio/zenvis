package com.coolxer.component;

import com.coolxer.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动加载组件
 * <p>
 * 应用启动时执行，用于初始化向量服务并记录系统信息
 * </p>
 *
 * @date 2023/7/3 10:47
 */
@Component
@Slf4j
@Order(1)
public class StartRunnerComponent implements CommandLineRunner {

  private static final Logger serviceStartStopLog = LoggerFactory.getLogger("com.coolxer.ServiceStartStopLog");

  private final VectorService vectorService;

  public StartRunnerComponent(VectorService vectorService) {
    this.vectorService = vectorService;
  }

  /**
   * 应用启动时执行
   * @param args 启动参数
   * @throws Exception 异常
   */
  @Override
  public void run(String... args) throws Exception {
    log.info("StartRunnerComponent is running");
    log.info("totalMemory: {}M", Runtime.getRuntime().totalMemory() / 1024 / 1024);
    log.info("maxMemory: {}M", Runtime.getRuntime().maxMemory() / 1024 / 1024);
    log.info("freeMemory: {}M", Runtime.getRuntime().freeMemory() / 1024 / 1024);
    log.info("userDir: {}", System.getProperty("user.dir"));

    try {
      vectorService.init();
      log.info("VectorService initialized successfully");
    } catch (Exception e) {
      log.error("Failed to initialize VectorService", e);
      throw e;
    }

    serviceStartStopLog.info("Service start up");
    Runtime.getRuntime().addShutdownHook(new Thread(() -> serviceStartStopLog.info("Service shutdown")));
  }
}