package com.coolxer.plugin.operation.service;

import com.coolxer.plugin.operation.model.OperationBoardDto;
import com.coolxer.plugin.operation.model.OperationBoardRecord;
import com.coolxer.plugin.operation.model.OperationBoardView;
import com.coolxer.plugin.operation.repository.OperationBoardRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class OperationBoardService {

    private final OperationBoardRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public OperationBoardService(
            OperationBoardRepository repository,
            ObjectMapper objectMapper,
            @Qualifier("pluginMysqlTransactionManager") PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    OperationBoardService(OperationBoardRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = null;
    }

    public boolean add(OperationBoardDto dto) {
        return inTransaction(() -> addInternal(dto));
    }

    private boolean addInternal(OperationBoardDto dto) {
        if (dto == null) {
            return false;
        }
        Long lastBoardId = dto.getLastBoard() != null && dto.getLastBoard() > 0
                ? dto.getLastBoard()
                : null;
        OperationBoardRecord previous = lastBoardId == null
                ? null
                : repository.findById(lastBoardId).orElse(null);
        if (lastBoardId != null && previous == null) {
            return false;
        }
        Long nextBoardId = previous == null ? null : previous.nextBoard();
        long id = repository.insert(dto, nextBoardId, icon(dto.getPolicy()));
        if (id <= 0) {
            return false;
        }
        if (previous != null) {
            repository.updateNext(previous.id(), id);
            if (nextBoardId != null) {
                repository.updateLast(nextBoardId, id);
            }
        }
        return true;
    }

    public void delete(long id) {
        inTransaction(() -> {
            deleteInternal(id);
            return null;
        });
    }

    private void deleteInternal(long id) {
        OperationBoardRecord board = repository.findById(id).orElse(null);
        if (board == null) {
            return;
        }
        if (board.lastBoard() != null) {
            repository.updateNext(board.lastBoard(), board.nextBoard());
        }
        if (board.nextBoard() != null) {
            repository.updateLast(board.nextBoard(), board.lastBoard());
        }
        repository.deleteById(id);
    }

    private <T> T inTransaction(Supplier<T> action) {
        if (transactionTemplate == null) {
            return action.get();
        }
        return transactionTemplate.execute(status -> action.get());
    }

    public List<List<OperationBoardView>> getAll() {
        List<OperationBoardRecord> records = repository.findAll();
        Map<Long, OperationBoardRecord> byId = new HashMap<>();
        records.forEach(record -> byId.put(record.id(), record));

        List<List<OperationBoardView>> result = new ArrayList<>();
        for (OperationBoardRecord head : records) {
            if (head.lastBoard() != null) {
                continue;
            }
            List<OperationBoardView> row = new ArrayList<>();
            Set<Long> visited = new HashSet<>();
            OperationBoardRecord current = head;
            while (current != null && visited.add(current.id())) {
                row.add(OperationBoardView.from(current));
                current = current.nextBoard() == null ? null : byId.get(current.nextBoard());
            }
            result.add(row);
        }
        return result;
    }

    public Object getChartById(long id) {
        OperationBoardRecord board = repository.findById(id).orElse(null);
        if (board == null) {
            return null;
        }
        String json = switch (board.view() == null ? "" : board.view()) {
            case "地图分布" -> "{\"title\":{\"text\":\"全国分布\"},\"tooltip\":{\"trigger\":\"item\",\"formatter\":\"{b}<br/>{c}\"},\"toolbox\":{\"show\":true,\"orient\":\"vertical\",\"left\":\"right\",\"top\":\"center\",\"feature\":{\"dataView\":{\"readOnly\":false},\"restore\":{},\"saveAsImage\":{}}},\"visualMap\":{\"min\":800,\"max\":50000,\"text\":[\"High\",\"Low\"],\"realtime\":false,\"calculable\":true,\"inRange\":{\"color\":[\"lightskyblue\",\"yellow\",\"orangered\"]}},\"series\":[{\"name\":\"用户分布\",\"type\":\"map\",\"map\":\"china\",\"label\":{\"show\":true},\"data\":[{\"name\":\"北京\",\"value\":20057.34},{\"name\":\"上海\",\"value\":15477.48},{\"name\":\"广东\",\"value\":31686.1},{\"name\":\"河南\",\"value\":6992.6},{\"name\":\"河北\",\"value\":44045.49},{\"name\":\"天津\",\"value\":40689.64},{\"name\":\"四川\",\"value\":37659.78},{\"name\":\"湖北\",\"value\":45180.97},{\"name\":\"安徽\",\"value\":55204.26},{\"name\":\"吉林\",\"value\":21900.9},{\"name\":\"黑龙江\",\"value\":4918.26},{\"name\":\"福建\",\"value\":5881.84},{\"name\":\"广西\",\"value\":4178.01},{\"name\":\"西藏\",\"value\":2227.92},{\"name\":\"新疆\",\"value\":2180.98},{\"name\":\"湖南\",\"value\":9172.94},{\"name\":\"江西\",\"value\":3368},{\"name\":\"香港\",\"value\":806.98}]}]}";
            case "漏斗图" -> "{\"title\":{\"text\":\"营销转化漏斗\",\"left\":\"center\"},\"tooltip\":{\"trigger\":\"item\",\"formatter\":\"{a} <br/>{b} : {c}%\"},\"legend\":{\"orient\":\"vertical\",\"left\":\"left\",\"data\":[\"访问\",\"咨询\",\"下单\",\"支付\",\"完成\"]},\"series\":[{\"name\":\"转化率\",\"type\":\"funnel\",\"left\":\"10%\",\"top\":60,\"bottom\":60,\"width\":\"80%\",\"height\":\"50%\",\"size\":[\"60%\",\"80%\"],\"sort\":\"descending\",\"gap\":2,\"label\":{\"show\":true,\"position\":\"inside\"},\"labelLine\":{\"length\":10,\"lineStyle\":{\"width\":1,\"type\":\"solid\"}},\"itemStyle\":{\"borderColor\":\"#fff\",\"borderWidth\":1},\"emphasis\":{\"label\":{\"fontSize\":20}},\"data\":[{\"value\":100,\"name\":\"访问\"},{\"value\":80,\"name\":\"咨询\"},{\"value\":60,\"name\":\"下单\"},{\"value\":40,\"name\":\"支付\"},{\"value\":20,\"name\":\"完成\"}]}]}";
            case "折线图" -> "{\"title\":{\"text\":\"用户活跃度趋势\",\"left\":\"center\"},\"tooltip\":{\"trigger\":\"axis\"},\"legend\":{\"orient\":\"vertical\",\"left\":\"left\",\"data\":[\"日活跃用户\",\"周活跃用户\"]},\"xAxis\":{\"type\":\"category\",\"boundaryGap\":false,\"data\":[\"1月\",\"2月\",\"3月\",\"4月\",\"5月\",\"6月\",\"7月\",\"8月\",\"9月\",\"10月\",\"11月\",\"12月\"]},\"yAxis\":{\"type\":\"value\"},\"series\":[{\"name\":\"日活跃用户\",\"type\":\"line\",\"data\":[200,230,201,254,290,330,310,320,332,301,334,390],\"markLine\":{\"data\":[{\"type\":\"average\",\"name\":\"平均值\"}]}},{\"name\":\"周活跃用户\",\"type\":\"line\",\"data\":[150,230,200,235,270,310,300,310,320,330,340,350],\"markLine\":{\"data\":[{\"type\":\"average\",\"name\":\"平均值\"}]}}]}";
            case "柱状图" -> "{\"title\":{\"text\":\"月度营销活动效果\",\"left\":\"center\"},\"tooltip\":{\"trigger\":\"axis\",\"axisPointer\":{\"type\":\"shadow\"}},\"legend\":{\"orient\":\"vertical\",\"left\":\"left\",\"data\":[\"销售额\",\"参与人数\"]},\"xAxis\":{\"type\":\"category\",\"data\":[\"1月\",\"2月\",\"3月\",\"4月\",\"5月\",\"6月\",\"7月\",\"8月\",\"9月\",\"10月\",\"11月\",\"12月\"]},\"yAxis\":[{\"type\":\"value\",\"name\":\"销售额（万元）\"},{\"type\":\"value\",\"name\":\"参与人数（人）\"}],\"series\":[{\"name\":\"销售额\",\"type\":\"bar\",\"yAxisIndex\":0,\"data\":[230,340,450,560,670,780,890,900,1000,1100,1200,1300]},{\"name\":\"参与人数\",\"type\":\"bar\",\"yAxisIndex\":1,\"data\":[1200,1300,1400,1500,1600,1700,1800,1900,2000,2100,2200,2300]}]}";
            case "饼图" -> "{\"title\":{\"text\":\"营销渠道分布\",\"left\":\"center\"},\"tooltip\":{\"trigger\":\"item\",\"formatter\":\"{a} <br/>{b}: {c} ({d}%)\"},\"legend\":{\"orient\":\"vertical\",\"left\":\"left\",\"data\":[\"社交媒体\",\"搜索引擎\",\"电子邮件\",\"线下活动\",\"合作伙伴\"]},\"series\":[{\"name\":\"渠道\",\"type\":\"pie\",\"radius\":\"50%\",\"data\":[{\"value\":335,\"name\":\"社交媒体\"},{\"value\":310,\"name\":\"搜索引擎\"},{\"value\":234,\"name\":\"电子邮件\"},{\"value\":135,\"name\":\"线下活动\"},{\"value\":154,\"name\":\"合作伙伴\"}],\"emphasis\":{\"itemStyle\":{\"shadowBlur\":10,\"shadowOffsetX\":0,\"shadowColor\":\"rgba(0, 0, 0, 0.5)\"}}}]}";
            default -> "{\"title\":{\"text\":\"销售情况\"},\"tooltip\":{},\"legend\":{\"data\":[\"销量\"]},\"xAxis\":{\"data\":[\"衬衫\",\"羊毛衫\",\"雪纺衫\",\"裤子\",\"高跟鞋\",\"袜子\"]},\"yAxis\":{},\"series\":[{\"name\":\"销量\",\"type\":\"bar\",\"data\":[35,25,2,86,63,56]}]}";
        };
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("运营图表配置解析失败", e);
        }
    }

    private String icon(String policy) {
        if (policy == null) {
            return null;
        }
        return switch (policy) {
            case "analysis_event" -> "fa-solid fa-arrow-trend-up";
            case "analysis_funnel" -> "fa-solid fa-filter";
            case "analysis_distribution" -> "fa-solid fa-chart-column";
            case "analysis_path" -> "fa-solid fa-draw-polygon";
            default -> null;
        };
    }
}
