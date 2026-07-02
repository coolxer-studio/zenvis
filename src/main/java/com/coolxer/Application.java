package com.coolxer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;


/**
 * 启动类
 */
@Slf4j
@EnableScheduling
@ServletComponentScan
@SpringBootApplication(
        exclude = {
                DataSourceAutoConfiguration.class
        },
        excludeName = {
                "org.springframework.ai.model.chat.memory.repository.jdbc.autoconfigure.JdbcChatMemoryRepositoryAutoConfiguration"
        }
)
public class Application {

    /**
     * 注入RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 主方法.
     */
    public static void main(String[] args) {
        new SpringApplicationBuilder(Application.class)
                .beanNameGenerator(new UniqueBeanNameGenerator())
                .run(args);
    }

    public static class UniqueBeanNameGenerator extends AnnotationBeanNameGenerator {

        /**
         * 如果自定义了beanName，就取自定义的，不然取默认的
         *
         * @param definition
         * @return
         */
        @Override
        protected String buildDefaultBeanName(BeanDefinition definition) {
            // 类名全路径
            return definition.getBeanClassName();
        }
    }


}
