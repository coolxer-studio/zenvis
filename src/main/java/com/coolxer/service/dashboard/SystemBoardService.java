package com.coolxer.service.dashboard;

import com.coolxer.model.dashboard.EntityStatisticsRange;
import com.coolxer.model.dashboard.vo.EntityStatisticsVo;
import com.coolxer.model.dashboard.vo.SystemOverviewVo;

public interface SystemBoardService {

    SystemOverviewVo overview();

    EntityStatisticsVo entityStatistics(EntityStatisticsRange range);
}
