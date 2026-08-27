package com.coolxer.controller.dashboard;

import com.coolxer.model.dashboard.EntityStatisticsRange;
import com.coolxer.model.dashboard.vo.EntityStatisticsVo;
import com.coolxer.model.dashboard.vo.SystemOverviewVo;
import com.coolxer.service.dashboard.SystemBoardService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeBoardControllerTest {

    @Test
    void delegatesOverviewAndEntityStatistics() {
        SystemBoardService service = mock(SystemBoardService.class);
        SystemOverviewVo overview = new SystemOverviewVo();
        EntityStatisticsVo statistics = new EntityStatisticsVo();
        when(service.overview()).thenReturn(overview);
        when(service.entityStatistics(EntityStatisticsRange.LAST_7_DAYS)).thenReturn(statistics);
        HomeBoardController controller = new HomeBoardController(service);

        assertThat(controller.overview().getData()).isSameAs(overview);
        assertThat(controller.entityStatistics(EntityStatisticsRange.LAST_7_DAYS).getData())
                .isSameAs(statistics);
        verify(service).overview();
        verify(service).entityStatistics(EntityStatisticsRange.LAST_7_DAYS);
    }
}
