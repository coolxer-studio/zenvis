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
import com.coolxer.service.retrieval.MetaDataService;
import com.coolxer.service.retrieval.QueryEngine;
import com.coolxer.service.system.BusinessServiceRegistryService;
import com.coolxer.service.system.PushTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemBoardServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-15T01:00:00Z");

    @Mock
    private MetaDataService metaDataService;
    @Mock
    private QueryEngine queryEngine;
    @Mock
    private PushTaskService pushTaskService;
    @Mock
    private AnalysisTaskRepository analysisTaskRepository;
    @Mock
    private BusinessServiceRegistryService businessServiceRegistryService;

    private SystemBoardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemBoardServiceImpl(
                metaDataService,
                queryEngine,
                pushTaskService,
                analysisTaskRepository,
                businessServiceRegistryService);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "retrievalTimeZone", "Asia/Shanghai");
    }

    @Test
    void overviewAggregatesPlatformMetricsAndStoppedTaskDoesNotDegradeSystem() {
        when(metaDataService.getAllDataEntity()).thenReturn(List.of(entity("asset"), entity("alarm")));
        when(pushTaskService.findAll()).thenReturn(List.of(pushTask("running"), pushTask("stopped")));
        when(businessServiceRegistryService.summary()).thenReturn(
                new BusinessServiceSummaryVo(3, 4, 3, 1, 0, 0, 0, 5, Date.from(NOW)));
        AnalysisTaskRepository.StatusCount runningCount = statusCount(AnalysisTaskStatus.RUNNING, 1);
        AnalysisTaskRepository.StatusCount successCount = statusCount(AnalysisTaskStatus.SUCCESS, 4);
        when(analysisTaskRepository.countGroupByStatus()).thenReturn(List.of(runningCount, successCount));
        when(analysisTaskRepository.count()).thenReturn(5L);
        AnalysisTask recent = new AnalysisTask().setName("日报分析").setStatus(AnalysisTaskStatus.SUCCESS);
        recent.setId(8);
        recent.setUpdateTime(Date.from(NOW));
        when(analysisTaskRepository.findTop10ByOrderByUpdateTimeDesc()).thenReturn(List.of(recent));

        SystemOverviewVo result = service.overview();

        assertThat(result.getStatus()).isEqualTo("HEALTHY");
        assertThat(result.getSummary().getEntityCount()).isEqualTo(2);
        assertThat(result.getSummary().getPushTaskCount()).isEqualTo(2);
        assertThat(result.getSummary().getAnalysisTaskCount()).isEqualTo(5);
        assertThat(result.getSummary().getBusinessServiceCount()).isEqualTo(3);
        assertThat(result.getServiceHealth().getRatio()).isEqualTo(75);
        assertThat(result.getBusinessServiceStatus())
                .extracting(SystemOverviewVo.BusinessServiceStatus::getStatus)
                .containsExactly("UP", "DEGRADED", "DOWN", "OFFLINE");
        assertThat(result.getBusinessServiceStatus())
                .extracting(SystemOverviewVo.BusinessServiceStatus::getCount)
                .containsExactly(3L, 1L, 0L, 0L);
        assertThat(result.getNotices()).filteredOn(item -> item.getKey().equals("push_task"))
                .singleElement().extracting(SystemOverviewVo.Notice::getCount).isEqualTo(1L);
        assertThat(result.getAnalysisTaskStatus()).hasSize(AnalysisTaskStatus.values().length);
        assertThat(result.getAnalysisTaskStatus()).filteredOn(item -> item.getStatus().equals("FAILED"))
                .singleElement().extracting(SystemOverviewVo.TaskStatus::getCount).isEqualTo(0L);
        assertThat(result.getRecentAnalysisTasks()).extracting(SystemOverviewVo.RecentAnalysisTask::getName)
                .containsExactly("日报分析");
    }

    @Test
    void overviewDegradesWhenPushTaskSourceIsUnavailableAndKeepsOtherMetrics() {
        when(metaDataService.getAllDataEntity()).thenReturn(List.of());
        when(pushTaskService.findAll()).thenThrow(new IllegalStateException("offline"));
        when(businessServiceRegistryService.summary()).thenReturn(
                new BusinessServiceSummaryVo(0, 0, 0, 0, 0, 0, 0, 0, Date.from(NOW)));
        when(analysisTaskRepository.countGroupByStatus()).thenReturn(List.of());
        when(analysisTaskRepository.findTop10ByOrderByUpdateTimeDesc()).thenReturn(List.of());

        SystemOverviewVo result = service.overview();

        assertThat(result.getStatus()).isEqualTo("DEGRADED");
        assertThat(result.isPushTaskSourceAvailable()).isFalse();
        assertThat(result.getSummary().getPushTaskCount()).isNull();
        assertThat(result.getServiceHealth().getRatio()).isNull();
        assertThat(result.getNotices()).filteredOn(item -> item.getKey().equals("push_task"))
                .singleElement().extracting(SystemOverviewVo.Notice::getInfo)
                .isEqualTo("数据推送服务不可用");
    }

    @Test
    void entityStatisticsReturnsTopTenAndReportsUnconfiguredEntities() {
        List<DataEntity> entities = new ArrayList<>();
        for (int index = 1; index <= 11; index++) {
            entities.add(entity("entity_" + index));
        }
        entities.add(entity("without_time"));
        when(metaDataService.getAllDataEntity()).thenReturn(entities);
        when(metaDataService.getDataAttributeByName(anyString(), anyString()))
                .thenAnswer(invocation -> "without_time".equals(invocation.getArgument(0))
                        ? null : timeAttribute());
        when(queryEngine.countByTimeRange(
                anyString(), anyString(), anyString(), nullable(String.class),
                any(Date.class), any(Date.class), anyBoolean()))
                .thenAnswer(invocation -> {
                    String table = invocation.getArgument(0);
                    long count = Long.parseLong(table.substring(table.lastIndexOf('_') + 1));
                    return Map.of("09:00", count);
                });

        EntityStatisticsVo result = service.entityStatistics(EntityStatisticsRange.TODAY);

        assertThat(result.getGranularity()).isEqualTo("HOUR");
        assertThat(result.getXAxis()).hasSize(24).startsWith("00:00").endsWith("23:00");
        assertThat(result.getSeries()).hasSize(10);
        assertThat(result.getSeries().get(0).getName()).isEqualTo("entity_11");
        assertThat(result.getRanking().get(0).getCount()).isEqualTo(11);
        assertThat(result.getOmittedEntityCount()).isEqualTo(1);
        assertThat(result.getSkippedEntities()).singleElement()
                .extracting(EntityStatisticsVo.SkippedEntity::getReason)
                .isEqualTo("MISSING_SYSTEM_INSERT_TIME");
    }

    @Test
    void entityStatisticsUsesExpectedBucketsForAllRangesAndSkipsFailedEntityOnly() {
        DataEntity entity = entity("asset");
        when(metaDataService.getAllDataEntity()).thenReturn(List.of(entity));
        when(metaDataService.getDataAttributeByName("asset", MetaDataConstants.INSERT_TIME_ATTRIBUTE))
                .thenReturn(timeAttribute());
        when(queryEngine.countByTimeRange(
                anyString(), anyString(), anyString(), nullable(String.class),
                any(Date.class), any(Date.class), anyBoolean()))
                .thenThrow(new IllegalStateException("table unavailable"));

        EntityStatisticsVo today = service.entityStatistics(EntityStatisticsRange.TODAY);
        EntityStatisticsVo yesterday = service.entityStatistics(EntityStatisticsRange.YESTERDAY);
        EntityStatisticsVo lastSevenDays = service.entityStatistics(EntityStatisticsRange.LAST_7_DAYS);

        assertThat(today.getXAxis()).hasSize(24);
        assertThat(yesterday.getXAxis()).hasSize(24);
        assertThat(lastSevenDays.getGranularity()).isEqualTo("DAY");
        assertThat(lastSevenDays.getXAxis()).containsExactly(
                "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12",
                "2026-07-13", "2026-07-14", "2026-07-15");
        assertThat(today.getSkippedEntities()).singleElement()
                .extracting(EntityStatisticsVo.SkippedEntity::getReason).isEqualTo("QUERY_FAILED");
    }

    private DataEntity entity(String name) {
        DataEntity entity = new DataEntity();
        entity.setName(name);
        entity.setLabel(name + "标签");
        entity.setTableName("table_" + name.replace("entity_", ""));
        return entity;
    }

    private DataAttribute timeAttribute() {
        DataAttribute attribute = new DataAttribute();
        attribute.setName(MetaDataConstants.INSERT_TIME_ATTRIBUTE);
        attribute.setColumnName(MetaDataConstants.INSERT_TIME_COLUMN);
        attribute.setColumnType(MetaDataConstants.INSERT_TIME_COLUMN_TYPE);
        return attribute;
    }

    private PushTaskVo pushTask(String status) {
        PushTaskVo task = new PushTaskVo();
        task.setStatus(status);
        return task;
    }

    private AnalysisTaskRepository.StatusCount statusCount(AnalysisTaskStatus status, long count) {
        AnalysisTaskRepository.StatusCount projection = mock(AnalysisTaskRepository.StatusCount.class);
        when(projection.getStatus()).thenReturn(status);
        when(projection.getCount()).thenReturn(count);
        return projection;
    }
}
