package com.coolxer.plugin.risk.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RiskTimelineService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Map<String, Object> timeline(String name) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeMonthsAgo = now.minusMonths(3);
        List<String> xAxis = new ArrayList<>();
        List<Integer> series = new ArrayList<>();

        for (LocalDateTime current = now;
             current.isAfter(threeMonthsAgo);
             current = current.minusHours(1)) {
            xAxis.add(current.format(FORMATTER));
            series.add(ThreadLocalRandom.current().nextInt(200));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title_name", name == null || name.isBlank() ? "全部风险" : name);
        result.put("x_axis", xAxis);
        result.put("series", series);
        return result;
    }
}
