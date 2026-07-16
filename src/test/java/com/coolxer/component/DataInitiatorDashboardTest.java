package com.coolxer.component;

import com.coolxer.commons.enums.DashboardType;
import com.coolxer.dao.mysql.entity.Dashboard;
import com.coolxer.dao.mysql.repository.DashboardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitiatorDashboardTest {

    @Mock
    private DashboardRepository dashboardRepository;

    @Test
    void migratesLegacyMessageBoardToSystemBoard() {
        Dashboard legacy = new Dashboard()
                .setName("系统总览")
                .setCode("msg-board")
                .setType(DashboardType.BUILT);
        when(dashboardRepository.findByCode("system-board")).thenReturn(Optional.empty());
        when(dashboardRepository.findByCode("msg-board")).thenReturn(Optional.of(legacy));
        DataInitiator initiator = initiator();

        ReflectionTestUtils.invokeMethod(initiator, "initDefaultDashboard");

        ArgumentCaptor<Dashboard> captor = ArgumentCaptor.forClass(Dashboard.class);
        verify(dashboardRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("system-board");
        assertThat(captor.getValue().getName()).isEqualTo("系统状态总览");
    }

    @Test
    void keepsExistingSystemBoardUntouched() {
        when(dashboardRepository.findByCode("system-board"))
                .thenReturn(Optional.of(new Dashboard().setCode("system-board")));
        DataInitiator initiator = initiator();

        ReflectionTestUtils.invokeMethod(initiator, "initDefaultDashboard");

        verify(dashboardRepository, never()).save(org.mockito.ArgumentMatchers.any(Dashboard.class));
        verify(dashboardRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    private DataInitiator initiator() {
        DataInitiator initiator = new DataInitiator();
        ReflectionTestUtils.setField(initiator, "dashboardRepository", dashboardRepository);
        return initiator;
    }
}
