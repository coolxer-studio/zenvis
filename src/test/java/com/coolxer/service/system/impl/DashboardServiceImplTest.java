package com.coolxer.service.system.impl;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import com.coolxer.model.system.dto.DashboardDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private DashboardRepository dashboardRepository;

    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardServiceImpl();
        ReflectionTestUtils.setField(dashboardService, "dashboardRepository", dashboardRepository);
    }

    @Test
    void builtDashboardRequiresCode() {
        DashboardDto dto = baseDto(DashboardType.BUILT);

        assertMissingParameter(() -> dashboardService.create(dto));
        verify(dashboardRepository, never()).save(any(Dashboard.class));
    }

    @Test
    void lowCodeDashboardDoesNotRequireCodeButRequiresConfigIndex() {
        DashboardDto dto = baseDto(DashboardType.LOW_CODE_PAGE);
        dto.setConfigIndex("dashboard-test");
        when(dashboardRepository.save(any(Dashboard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dashboard dashboard = dashboardService.create(dto);

        assertThat(dashboard.getCode()).isNull();
        assertThat(dashboard.getConfigIndex()).isEqualTo("dashboard-test");

        DashboardDto missingConfigIndex = baseDto(DashboardType.LOW_CODE_PAGE);
        assertMissingParameter(() -> dashboardService.create(missingConfigIndex));
    }

    @Test
    void htmlDashboardDoesNotRequireCodeButRequiresHtmlPath() {
        DashboardDto dto = baseDto(DashboardType.HTML_PAGE);
        dto.setHtmlPath("/html/test.html");
        when(dashboardRepository.save(any(Dashboard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dashboard dashboard = dashboardService.create(dto);

        assertThat(dashboard.getCode()).isNull();
        assertThat(dashboard.getHtmlPath()).isEqualTo("/html/test.html");

        DashboardDto missingHtmlPath = baseDto(DashboardType.HTML_PAGE);
        assertMissingParameter(() -> dashboardService.create(missingHtmlPath));
    }

    @Test
    void linkDashboardDoesNotRequireCodeButRequiresUrl() {
        DashboardDto dto = baseDto(DashboardType.LINK);
        dto.setUrl("https://example.com");
        when(dashboardRepository.save(any(Dashboard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dashboard dashboard = dashboardService.create(dto);

        assertThat(dashboard.getCode()).isNull();
        assertThat(dashboard.getUrl()).isEqualTo("https://example.com");

        DashboardDto missingUrl = baseDto(DashboardType.LINK);
        assertMissingParameter(() -> dashboardService.create(missingUrl));
    }

    @Test
    void updateUsesSameTypeValidation() {
        DashboardDto dto = baseDto(DashboardType.BUILT);

        assertMissingParameter(() -> dashboardService.update(1L, dto));
        verify(dashboardRepository, never()).findById(any());

        dto.setCode("msg-board");
        Dashboard existing = new Dashboard();
        existing.setId(1);
        when(dashboardRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dashboardRepository.save(any(Dashboard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(dashboardService.update(1L, dto)).isTrue();
        ArgumentCaptor<Dashboard> captor = ArgumentCaptor.forClass(Dashboard.class);
        verify(dashboardRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("msg-board");
    }

    private static DashboardDto baseDto(DashboardType type) {
        DashboardDto dto = new DashboardDto();
        dto.setName("测试看板");
        dto.setType(type);
        return dto;
    }

    private static void assertMissingParameter(Runnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.DASHBOARD_PARAMETER_MISS_ERROR.getCode());
    }
}
