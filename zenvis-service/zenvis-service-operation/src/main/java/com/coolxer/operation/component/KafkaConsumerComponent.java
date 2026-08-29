package com.coolxer.operation.component;

import com.coolxer.operation.commons.enums.MsgTypeEnum;
import com.coolxer.operation.commons.enums.PlatformEnum;
import com.coolxer.operation.model.AndroidActivity;
import com.coolxer.operation.model.AndroidStart;
import com.coolxer.operation.model.FactMsg;
import com.coolxer.operation.service.ProductService;
import com.coolxer.operation.utils.JacksonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * kafka 消费者
 *
 * @author yaoqi.li
 */
@Slf4j
@Component
public class KafkaConsumerComponent {

    @Autowired
    private ProductService productService;

  /**
   * 入口函数，接收kafka中的数据
   *
   * @param records
   */
  @KafkaListener(topics = {"#{'${spring.kafka.consumer.listen-topics}'.split(',')}"})
  public void listen(List<ConsumerRecord<String, String>> records) {
    // 推给线程池处理
    batchProcessingKafkaData(records);
  }


  /**
   * 处理kafka中拿到的消息
   *
   * @param records Kafka消息
   */
  @Async
  public void batchProcessingKafkaData(List<ConsumerRecord<String, String>> records) {
      // kafka的消息集合转化
      records.stream()
              .filter(record -> Objects.nonNull(record) && Objects.nonNull(record.value()))
              .forEach(record -> {
                  try {
                      String topicPrefix = StringUtils.substringBefore(record.topic(),"_");
                      PlatformEnum platform = PlatformEnum.valueOf(StringUtils.toRootUpperCase(topicPrefix));
                      String topicSuffix = StringUtils.substringAfterLast(record.topic(),"_");
                      MsgTypeEnum msgType = MsgTypeEnum.valueOf(StringUtils.toRootUpperCase(topicSuffix));
                      switch (msgType){
                          case START:
                              processStart(platform,record.value());
                              break;
                          case ACTIVITY:
                              processActivity(platform,record.value());
                              break;
                          default:
                              log.warn("unknown platform:{}", platform.getType());
                              break;
                      }
                  } catch (Exception e) {
                      log.error("record to deviceModel error:{}", record, e);
                  }
              });
  }

    private void processStart(PlatformEnum platform, String startMsg) throws JsonProcessingException {
        switch (platform){
            case ANDROID:
                AndroidStart androidStart = JacksonUtil.toObject(startMsg, AndroidStart.class);
                productService.sendAndroidStart(androidStart);
                break;
            default:
                log.warn("unknown platform:{}", platform.getType());
                break;
        }
    }

    private void processActivity(PlatformEnum platform, String startMsg) throws JsonProcessingException {
        switch (platform){
            case ANDROID:
                AndroidActivity androidActivity = JacksonUtil.toObject(startMsg, AndroidActivity.class);
                productService.sendAndroidActivity(androidActivity);
                break;
            default:
                log.warn("unknown platform:{}", platform.getType());
                break;
        }
    }

}
