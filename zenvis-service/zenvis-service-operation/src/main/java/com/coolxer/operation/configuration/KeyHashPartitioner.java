package com.coolxer.operation.configuration;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;

import java.util.Map;

/**
 * 自定义kafka分区规则，按照key的hashCode取模计算分区，保证相同key进入相同分区
 *
 * @author yaoqi.li
 */
@Slf4j
public class KeyHashPartitioner implements Partitioner {

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        String partitionKey = String.valueOf(key);
        int partitionSize  = cluster.partitionsForTopic(topic).size();
        if (StringUtils.isEmpty(partitionKey) || partitionSize <= 1) {
            return 0;
        }
        return Math.abs(partitionKey.hashCode()) % partitionSize;
    }

    @Override
    public void close() {

    }

    @Override
    public void configure(Map<String, ?> map) {

    }
}