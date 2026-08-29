package com.coolxer.operation;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;


/**
 * @author yaoqi.li
 */
@EnableScheduling
@EnableAsync
@SpringBootApplication
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
