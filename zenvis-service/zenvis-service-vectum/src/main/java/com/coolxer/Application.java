package com.coolxer;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 数据服务应用启动类
 * <p>
 * 提供向量任务管理、日志监控等核心功能
 * </p>
 */
@SpringBootApplication
@EnableAsync
public class Application {

	public static void main(String[] args) {
    new SpringApplicationBuilder(Application.class)
            .beanNameGenerator(new UniqueBeanNameGenerator())
            .run(args);
  }

  public static class UniqueBeanNameGenerator extends AnnotationBeanNameGenerator {
    /**
     * 如果自定义了beanName，就取自定义的，不然取默认的
     * @param definition
     * @return
     */
    @Override
    protected String buildDefaultBeanName(BeanDefinition definition) {
      return definition.getBeanClassName();// 类名全路径
    }
  }

}
