package com.coolxer.risk.component;

import com.coolxer.risk.model.FactMsg;
import com.coolxer.risk.model.RatingData;
import com.coolxer.risk.service.ProductService;
import com.coolxer.risk.service.RatingCalculateService;
import com.coolxer.risk.utils.JacksonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
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

    @Autowired
    private RatingCalculateService ratingCalculateService;

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
        // kafka的消息集合转化为FactMsg集合
        List<FactMsg> factMsgList = records.stream()
                .filter(record -> Objects.nonNull(record) && Objects.nonNull(record.value()))
                .map(record -> {
                    FactMsg factMsg = null;
                    try {
                        factMsg = JacksonUtil.toObject(record.value(), FactMsg.class);
                        productService.sendFactMsg(factMsg);
                    } catch (JsonProcessingException e) {
                        log.error("record to factMsg error:{}", record, e);
                    }
                    return factMsg;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // FactMsg集合处理：1.过滤 2.转化为RatingData 3.本批次聚合（按照partitionKey）
        // 按照设备启动汇聚数据集合，用于保存最近依次启动的数据
        Map<String, List<RatingData>> groupedRatingDataWithStart = new HashMap<>(200);
        // 按照设备时间汇聚数据集合，用于后续数据更新和评级计算
        Map<String, List<RatingData>> groupedRatingDataWithTime = new HashMap<>(200);
        factMsgList.stream()
                .filter(factMsg -> Objects.nonNull(factMsg) && Objects.nonNull(factMsg.getAgendas()) && factMsg.getAgendas().size() > 0)
                .map(factMsg -> factMsg.toRatingData())
                .filter(Objects::nonNull)
                .forEach(ratingData -> {
                    // 添加启动汇聚数据
                    String startPartitionKey = ratingData.spitStartPartitionKey();
                    List<RatingData> ratingDataListForStartKey = groupedRatingDataWithStart.getOrDefault(startPartitionKey, new ArrayList<>());
                    ratingDataListForStartKey.add(ratingData);
                    groupedRatingDataWithStart.put(startPartitionKey, ratingDataListForStartKey);
                    // 添加时间汇聚数据
                    String timePartitionKey = ratingData.spitTimePartitionKey();
                    List<RatingData> ratingDataListForTimeKey = groupedRatingDataWithTime.getOrDefault(timePartitionKey, new ArrayList<>());
                    ratingDataListForTimeKey.add(ratingData);
                    groupedRatingDataWithTime.put(timePartitionKey, ratingDataListForTimeKey);
                });
        // 更新最近启动数据
        ratingCalculateService.updateLastStart(groupedRatingDataWithStart);
        // 开始处理威胁指数逻辑
        ratingCalculateService.rating(groupedRatingDataWithTime);
    }


}
