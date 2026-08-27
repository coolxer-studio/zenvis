package com.coolxer.utils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 通用工具类
 */
public class CommonUtil {

    /**
     * 验证UUID格式是否有效
     *
     * @param uuid UUID字符串
     */
    public static boolean isValidUUID(String uuid) {
        if (uuid == null) {
            return false;
        }
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 创建选项列表
     * 将列表转换为包含label和value的Map列表，支持label和value使用不同的值
     *
     * @param items       源数据列表
     * @param labelMapper 将源数据转换为label的函数
     * @param valueMapper 将源数据转换为value的函数
     * @return 选项列表
     */
    public static <T> List<Map<String, String>> createOptions(List<T> items, Function<T, String> labelMapper, Function<T, String> valueMapper) {
        return items.stream()
                .map(item -> {
                    Map<String, String> option = new HashMap<>(2);
                    option.put("label", labelMapper.apply(item));
                    option.put("value", valueMapper.apply(item));
                    return option;
                })
                .toList();
    }

    /**
     * 通用的按周统计方法，将List<Map<String, Object>>转为Map<String, Object>
     *
     * @param supplier 查询数据的函数
     * @return 统计结果
     */
    public static Map<String, Object> getStatisticsMap(Supplier<List<Map<String, Object>>> supplier) {
        List<Map<String, Object>> counts = supplier.get();
        Map<String, Object> result = new HashMap<>();
        counts.forEach(count -> {
            String date = ((Object) count.get("group_key")).toString();
            long countValue = ((BigDecimal) count.get("count")).longValue();
            result.put(date, countValue);
        });
        return result;
    }
}
