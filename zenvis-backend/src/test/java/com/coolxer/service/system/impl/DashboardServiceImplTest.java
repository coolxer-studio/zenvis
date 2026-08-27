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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

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
        dto.setHtmlPath("html/test.html");
        when(dashboardRepository.save(any(Dashboard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dashboard dashboard = dashboardService.create(dto);

        assertThat(dashboard.getCode()).isNull();
        assertThat(dashboard.getHtmlPath()).isEqualTo("html/test.html");

        DashboardDto missingHtmlPath = baseDto(DashboardType.HTML_PAGE);
        assertMissingParameter(() -> dashboardService.create(missingHtmlPath));
    }

    @Test
    void htmlDashboardRejectsNonRelativePaths() {
        List<String> invalidPaths = List.of(
                "/html/test.html",
                "https://example.com/test.html",
                "../test.html",
                "html/../test.html",
                "html\\test.html",
                "html/test.html?mode=full",
                "html/test.html#main"
        );

        for (String invalidPath : invalidPaths) {
            DashboardDto dto = baseDto(DashboardType.HTML_PAGE);
            dto.setHtmlPath(invalidPath);
            assertThatThrownBy(() -> dashboardService.create(dto))
                    .isInstanceOf(ApiException.class)
                    .extracting("code")
                    .isEqualTo(ResultCodeEnum.DASHBOARD_HTML_PATH_INVALID.getCode());
        }
        verify(dashboardRepository, never()).save(any(Dashboard.class));
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
        verify(dashboardRepository, never()).findAllForUpdate();

        dto.setCode("system-board");
        Dashboard existing = new Dashboard().setIsDefault(true);
        existing.setId(1);
        when(dashboardRepository.findAllForUpdate()).thenReturn(new ArrayList<>(List.of(existing)));

        assertThat(dashboardService.update(1L, dto)).isTrue();
        assertThat(existing.getCode()).isEqualTo("system-board");
        assertThat(existing.getIsDefault()).isTrue();
    }

    @Test
    void creatingNewDefaultReplacesExistingDefault() {
        Dashboard existing = dashboard(1, "system-board", true);
        when(dashboardRepository.findAllForUpdate()).thenReturn(new ArrayList<>(List.of(existing)));
        when(dashboardRepository.save(any(Dashboard.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DashboardDto dto = baseDto(DashboardType.LINK);
        dto.setUrl("https://example.com");
        dto.setIsDefault(true);

        Dashboard created = dashboardService.create(dto);

        assertThat(existing.getIsDefault()).isFalse();
        assertThat(created.getIsDefault()).isTrue();
    }

    @Test
    void updatingNewDefaultReplacesExistingDefault() {
        Dashboard existingDefault = dashboard(1, "system-board", true);
        Dashboard replacement = dashboard(2, "replacement", false);
        when(dashboardRepository.findAllForUpdate())
                .thenReturn(new ArrayList<>(List.of(existingDefault, replacement)));
        DashboardDto dto = baseDto(DashboardType.BUILT);
        dto.setCode("replacement");
        dto.setIsDefault(true);

        assertThat(dashboardService.update(2L, dto)).isTrue();

        assertThat(existingDefault.getIsDefault()).isFalse();
        assertThat(replacement.getIsDefault()).isTrue();
    }

    @Test
    void updateWithoutDefaultFlagPreservesCurrentDefault() {
        Dashboard existing = dashboard(1, "system-board", true);
        when(dashboardRepository.findAllForUpdate()).thenReturn(new ArrayList<>(List.of(existing)));
        DashboardDto dto = baseDto(DashboardType.BUILT);
        dto.setCode("system-board");

        assertThat(dashboardService.update(1L, dto)).isTrue();

        assertThat(existing.getIsDefault()).isTrue();
    }

    @Test
    void cannotUnsetOrDeleteCurrentDefault() {
        Dashboard existing = dashboard(1, "system-board", true);
        when(dashboardRepository.findAllForUpdate()).thenReturn(new ArrayList<>(List.of(existing)));
        DashboardDto dto = baseDto(DashboardType.BUILT);
        dto.setCode("system-board");
        dto.setIsDefault(false);

        assertThatThrownBy(() -> dashboardService.update(1L, dto))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.DASHBOARD_DEFAULT_REQUIRED.getCode());
        assertThatThrownBy(() -> dashboardService.delete(1L))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.DASHBOARD_DEFAULT_DELETE_NOT_ALLOWED.getCode());
    }

    @Test
    void bulkOperationsRejectAmbiguousDefaultChangesAndDefaultDeletion() {
        DashboardDto dto = baseDto(DashboardType.LINK);
        dto.setUrl("https://example.com");
        dto.setIsDefault(true);

        assertThatThrownBy(() -> dashboardService.bulkUpdate(List.of(1L, 2L), dto))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.DASHBOARD_MULTIPLE_DEFAULT_NOT_ALLOWED.getCode());

        Dashboard existingDefault = dashboard(1, "system-board", true);
        Dashboard other = dashboard(2, "other", false);
        when(dashboardRepository.findAllForUpdate())
                .thenReturn(new ArrayList<>(List.of(existingDefault, other)));

        assertThatThrownBy(() -> dashboardService.deleteByIds(List.of(1L, 2L)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.DASHBOARD_DEFAULT_DELETE_NOT_ALLOWED.getCode());
        verify(dashboardRepository, never()).deleteAll(any());
    }

    private static DashboardDto baseDto(DashboardType type) {
        DashboardDto dto = new DashboardDto();
        dto.setName("测试看板");
        dto.setType(type);
        return dto;
    }

    private static Dashboard dashboard(int id, String code, boolean isDefault) {
        Dashboard dashboard = new Dashboard()
                .setName(code)
                .setCode(code)
                .setType(DashboardType.BUILT)
                .setIsDefault(isDefault);
        dashboard.setId(id);
        return dashboard;
    }

    private static void assertMissingParameter(Runnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ResultCodeEnum.DASHBOARD_PARAMETER_MISS_ERROR.getCode());
    }
}
