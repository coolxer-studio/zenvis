package com.coolxer.service.retrieval;

import com.coolxer.model.retrieval.analytics.AnalyticsResponse;
import com.coolxer.model.retrieval.analytics.DistributionQueryRequest;
import com.coolxer.model.retrieval.analytics.OverviewQueryRequest;
import com.coolxer.model.retrieval.analytics.RelationQueryRequest;
import com.coolxer.model.retrieval.analytics.RelationTimelineQueryRequest;
import com.coolxer.model.retrieval.analytics.SummaryQueryRequest;
import com.coolxer.model.retrieval.analytics.TrendQueryRequest;
import com.coolxer.model.retrieval.analytics.ValueStatisticsQueryRequest;

public interface EntityAnalyticsService {

    AnalyticsResponse overview(OverviewQueryRequest request);

    AnalyticsResponse summary(SummaryQueryRequest request);

    AnalyticsResponse trend(TrendQueryRequest request);

    AnalyticsResponse distribution(DistributionQueryRequest request);

    AnalyticsResponse valueStatistics(ValueStatisticsQueryRequest request);

    AnalyticsResponse relations(RelationQueryRequest request);

    AnalyticsResponse relationTimeline(RelationTimelineQueryRequest request);
}
