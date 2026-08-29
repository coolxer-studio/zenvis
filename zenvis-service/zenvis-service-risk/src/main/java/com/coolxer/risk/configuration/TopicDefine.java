package com.coolxer.risk.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 定义kafka中的topic配置
 *
 * @author yaoqi.li
 */
@Configuration
public class TopicDefine {

    @Value("${kafka.topic.partitions:1}")
    private int partitions;
    @Value("${kafka.topic.replicas:1}")
    private int replicas;

    public static final String TOPIC_RISK_ALL = "risk_all";
    @Bean(TOPIC_RISK_ALL)
    public NewTopic TopicForRiskAll() {
        return TopicBuilder.name(TOPIC_RISK_ALL)
                .partitions(partitions)
                .replicas(replicas)
                .compact()
                .build();
    }
}
