package com.coolxer.service.dashboard.impl;

import com.coolxer.commons.enums.AnalysisTaskStatus;
import com.coolxer.dao.mysql.entity.AnalysisTask;
import com.coolxer.dao.mysql.repository.AnalysisTaskRepository;
import com.coolxer.model.dashboard.EntityStatisticsRange;
import com.coolxer.model.dashboard.vo.EntityStatisticsVo;
import com.coolxer.model.dashboard.vo.SystemOverviewVo;
import com.coolxer.model.retrieval.meta.DataAttribute;
import com.coolxer.model.retrieval.meta.DataEntity;
import com.coolxer.model.retrieval.meta.MetaDataConstants;
import com.coolxer.model.system.vo.BusinessServiceSummaryVo;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.dashboard.SystemBoardService;
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.QueryEngine;
import com.coolxer.service.system.BusinessServiceRegistryService;
import com.coolxer.service.system.PushTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class SystemBoardServiceImpl implements SystemBoardService {

    private static final int ENTITY_DISPLAY_LIMIT = 10;
    private static final int RECENT_TASK_LIMIT = 10;

    private final MetaDataService metaDataService;
    private final QueryEngine queryEngine;
    private final PushTaskService pushTaskService;
    private final AnalysisTaskRepository analysisTaskRepository;
    private final BusinessServiceRegistryService businessServiceRegistryService;

    @Value("${app.retrieval.time-zone:Asia/Shanghai}")
    private String retrievalTimeZone = "Asia/Shanghai";

    private Clock clock = Clock.systemUTC();

    public SystemBoardServiceImpl(MetaDataService metaDataService,
                                  QueryEngine queryEngine,
                                  PushTaskService pushTaskService,
                                  AnalysisTaskRepository analysisTaskRepository,
                                  BusinessServiceRegistryService businessServiceRegistryService) {
        this.metaDataService = metaDataService;
        this.queryEngine = queryEngine;
        this.pushTaskService = pushTaskService;
        this.analysisTaskRepository = analysisTaskRepository;
        this.businessServiceRegistryService = businessServiceRegistryService;
    }

    @Override
    public SystemOverviewVo overview() {
        Date checkedAt = Date.from(clock.instant());
        List<DataEntity> entities = safeEntities();
        BusinessServiceSummaryVo businessSummary = businessServiceRegistryService.summary();
        Map<AnalysisTaskStatus, Long> taskStatusCounts = analysisTaskStatusCounts();

        boolean pushTaskSourceAvailable = true;
        List<PushTaskVo> pushTasks = Collections.emptyList();
        try {
            pushTasks = pushTaskService.findAll();
        } catch (Exception ex) {
            pushTaskSourceAvailable = false;
            log.warn("读取数据推送任务失败，看板将降级展示: {}", ex.getMessage());
        }

        long stoppedPushTasks = pushTasks.stream().filter(this::isStoppedPushTask).count();
        long abnormalPushTasks = pushTasks.stream().filter(this::isAbnormalPushTask).count();
        long waitingApprovalTasks = taskStatusCounts.getOrDefault(AnalysisTaskStatus.WAITING_APPROVAL, 0L);
        boolean degraded = !pushTaskSourceAvailable || abnormalPushTasks > 0 || businessSummary.getAbnormalCount() > 0;

        List<SystemOverviewVo.Notice> notices = List.of(
                notice("business_service", businessSummary.getAbnormalCount(),
                        businessSummary.getAbnormalCount() == 0 ? "业务应用服务运行正常" : "存在异常业务应用服务",
                        businessSummary.getAbnormalCount() == 0 ? "NORMAL" : "ERROR"),
                notice("push_task", pushTaskSourceAvailable ? stoppedPushTasks + abnormalPushTasks : 0,
                        pushTaskNotice(pushTaskSourceAvailable, stoppedPushTasks, abnormalPushTasks),
                        !pushTaskSourceAvailable || abnormalPushTasks > 0 ? "ERROR"
                                : stoppedPushTasks > 0 ? "WARNING" : "NORMAL"),
                notice("analysis_approval", waitingApprovalTasks,
                        waitingApprovalTasks == 0 ? "没有待审批AI分析任务" : "存在待审批AI分析任务",
                        waitingApprovalTasks == 0 ? "NORMAL" : "WARNING")
        );

        Integer healthRatio = businessSummary.getInstanceCount() == 0 ? null
                : (int) Math.round(businessSummary.getUpCount() * 100.0 / businessSummary.getInstanceCount());
        List<SystemOverviewVo.TaskStatus> taskStatuses = Arrays.stream(AnalysisTaskStatus.values())
                .map(status -> new SystemOverviewVo.TaskStatus(
                        status.name(), status.getDescription(), taskStatusCounts.getOrDefault(status, 0L)))
                .toList();

        List<SystemOverviewVo.RecentAnalysisTask> recentTasks = analysisTaskRepository
                .findTop10ByOrderByUpdateTimeDesc().stream()
                .limit(RECENT_TASK_LIMIT)
                .map(this::recentTask)
                .toList();

        return SystemOverviewVo.builder()
                .checkedAt(checkedAt)
                .status(degraded ? "DEGRADED" : "HEALTHY")
                .statusDescription(degraded ? "部分异常" : "正常运行")
                .summary(new SystemOverviewVo.Summary(
                        entities.size(),
                        pushTaskSourceAvailable ? (long) pushTasks.size() : null,
                        analysisTaskRepository.count(),
                        businessSummary.getServiceCount()))
                .notices(notices)
                .serviceHealth(new SystemOverviewVo.ServiceHealth(
                        healthRatio,
                        businessSummary.getInstanceCount(),
                        businessSummary.getUpCount(),
                        businessSummary.getAbnormalCount(),
                        businessSummary.getEventCount24h()))
                .businessServiceStatus(List.of(
                        businessServiceStatus("UP", "正常", businessSummary.getUpCount()),
                        businessServiceStatus("DEGRADED", "性能下降", businessSummary.getDegradedCount()),
                        businessServiceStatus("DOWN", "故障", businessSummary.getDownCount()),
                        businessServiceStatus("OFFLINE", "离线", businessSummary.getOfflineCount())))
                .analysisTaskStatus(taskStatuses)
                .recentAnalysisTasks(recentTasks)
                .pushTaskSourceAvailable(pushTaskSourceAvailable)
                .build();
    }

    private SystemOverviewVo.BusinessServiceStatus businessServiceStatus(
            String status, String description, long count) {
        return new SystemOverviewVo.BusinessServiceStatus(status, description, count);
    }

    @Override
    public EntityStatisticsVo entityStatistics(EntityStatisticsRange range) {
        EntityStatisticsRange selectedRange = range == null ? EntityStatisticsRange.TODAY : range;
        RangeWindow window = rangeWindow(selectedRange);
        List<String> xAxis = buildXAxis(window);
        List<EntityStatisticsVo.SkippedEntity> skippedEntities = new ArrayList<>();
        List<EntityStatisticsCandidate> candidates = new ArrayList<>();

        for (DataEntity entity : safeEntities()) {
            String label = StringUtils.defaultIfBlank(entity.getLabel(), entity.getName());
            DataAttribute attribute = metaDataService.getDataAttributeByName(
                    entity.getName(), MetaDataConstants.INSERT_TIME_ATTRIBUTE);
            if (attribute == null) {
                skippedEntities.add(new EntityStatisticsVo.SkippedEntity(
                        entity.getName(), label, "MISSING_SYSTEM_INSERT_TIME", "平台创建时间属性不存在"));
                continue;
            }
            try {
                Map<String, Long> counts = queryEngine.countByTimeRange(
                        entity.getTableName(),
                        attribute.getColumnName(),
                        attribute.getColumnType(),
                        null,
                        Date.from(window.start().toInstant()),
                        Date.from(window.end().toInstant()),
                        window.hourly());
                List<Long> values = xAxis.stream().map(key -> counts.getOrDefault(key, 0L)).toList();
                long total = values.stream().mapToLong(Long::longValue).sum();
                if (total > 0) {
                    candidates.add(new EntityStatisticsCandidate(entity.getName(), label, values, total));
                }
            } catch (Exception ex) {
                log.warn("实体趋势统计失败，entity={}, reason={}", entity.getName(), ex.getMessage());
                skippedEntities.add(new EntityStatisticsVo.SkippedEntity(
                        entity.getName(), label, "QUERY_FAILED", "趋势统计查询失败"));
            }
        }

        candidates.sort(Comparator.comparingLong(EntityStatisticsCandidate::total).reversed()
                .thenComparing(EntityStatisticsCandidate::name));
        List<EntityStatisticsCandidate> displayed = candidates.stream().limit(ENTITY_DISPLAY_LIMIT).toList();

        return EntityStatisticsVo.builder()
                .range(selectedRange.name())
                .startTime(Date.from(window.start().toInstant()))
                .endTime(Date.from(window.end().toInstant()))
                .granularity(window.hourly() ? "HOUR" : "DAY")
                .xAxis(xAxis)
                .series(displayed.stream()
                        .map(item -> new EntityStatisticsVo.EntitySeries(
                                item.name(), item.label(), item.values(), item.total()))
                        .toList())
                .ranking(displayed.stream()
                        .map(item -> new EntityStatisticsVo.EntityRanking(
                                item.name(), item.label(), item.total()))
                        .toList())
                .omittedEntityCount(Math.max(0, candidates.size() - displayed.size()))
                .skippedEntities(skippedEntities)
                .build();
    }

    private List<DataEntity> safeEntities() {
        List<DataEntity> entities = metaDataService.getAllDataEntity();
        return entities == null ? Collections.emptyList() : entities;
    }

    private Map<AnalysisTaskStatus, Long> analysisTaskStatusCounts() {
        Map<AnalysisTaskStatus, Long> counts = new EnumMap<>(AnalysisTaskStatus.class);
        for (AnalysisTaskRepository.StatusCount statusCount : analysisTaskRepository.countGroupByStatus()) {
            if (statusCount.getStatus() != null) {
                counts.put(statusCount.getStatus(), statusCount.getCount());
            }
        }
        return counts;
    }

    private SystemOverviewVo.Notice notice(String key, long count, String info, String level) {
        return new SystemOverviewVo.Notice(key, count, info, level);
    }

    private String pushTaskNotice(boolean sourceAvailable, long stopped, long abnormal) {
        if (!sourceAvailable) {
            return "数据推送服务不可用";
        }
        if (abnormal > 0) {
            return "存在异常数据推送任务";
        }
        if (stopped > 0) {
            return "存在未运行数据推送任务";
        }
        return "数据推送任务运行正常";
    }

    private boolean isStoppedPushTask(PushTaskVo task) {
        return "stopped".equals(normalizedPushTaskStatus(task));
    }

    private boolean isAbnormalPushTask(PushTaskVo task) {
        String status = normalizedPushTaskStatus(task);
        return "error".equals(status) || status.contains("[error]");
    }

    private String normalizedPushTaskStatus(PushTaskVo task) {
        return task == null ? "" : StringUtils.lowerCase(StringUtils.trimToEmpty(task.getStatus()), Locale.ROOT);
    }

    private SystemOverviewVo.RecentAnalysisTask recentTask(AnalysisTask task) {
        AnalysisTaskStatus status = task.getStatus();
        return new SystemOverviewVo.RecentAnalysisTask(
                task.getId(),
                task.getName(),
                status == null ? null : status.name(),
                status == null ? null : status.getDescription(),
                task.getUpdateTime());
    }

    private RangeWindow rangeWindow(EntityStatisticsRange range) {
        ZoneId zoneId = ZoneId.of(retrievalTimeZone);
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), zoneId);
        ZonedDateTime todayStart = now.toLocalDate().atStartOfDay(zoneId);
        return switch (range) {
            case TODAY -> new RangeWindow(todayStart, now, true);
            case YESTERDAY -> new RangeWindow(todayStart.minusDays(1), todayStart, true);
            case LAST_7_DAYS -> new RangeWindow(todayStart.minusDays(6), now, false);
        };
    }

    private List<String> buildXAxis(RangeWindow window) {
        if (window.hourly()) {
            List<String> hours = new ArrayList<>(24);
            for (int hour = 0; hour < 24; hour++) {
                hours.add(String.format(Locale.ROOT, "%02d:00", hour));
            }
            return hours;
        }
        List<String> days = new ArrayList<>(7);
        LocalDate start = window.start().toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        for (int day = 0; day < 7; day++) {
            days.add(start.plusDays(day).format(formatter));
        }
        return days;
    }

    private record RangeWindow(ZonedDateTime start, ZonedDateTime end, boolean hourly) {
    }

    private record EntityStatisticsCandidate(String name, String label, List<Long> values, long total) {
    }
}
