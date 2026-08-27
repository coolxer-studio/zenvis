package com.coolxer.controller.system;

import com.coolxer.aop.ApiExceptionHandler;
import com.coolxer.commons.enums.BusinessServiceEffectiveStatus;
import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.system.dto.BusinessServiceHeartbeatDto;
import com.coolxer.model.system.dto.BusinessServiceInstanceSearchDto;
import com.coolxer.model.system.vo.BusinessServiceHeartbeatAckVo;
import com.coolxer.service.system.BusinessServiceRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BusinessServiceControllerTest {

    @Mock
    private BusinessServiceRegistryService registryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new BusinessServicePublicController(registryService),
                        new BusinessServiceController(registryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(JacksonConfig.OBJECT_MAPPER))
                .build();
    }

    @Test
    void heartbeatUsesSnakeCaseContractAndUnifiedResponse() throws Exception {
        Date receivedAt = Date.from(Instant.parse("2026-07-15T00:00:00Z"));
        when(registryService.reportHeartbeat(any(BusinessServiceHeartbeatDto.class), anyString()))
                .thenReturn(new BusinessServiceHeartbeatAckVo(
                        "order-api", "order-api-1", true, receivedAt,
                        BusinessServiceEffectiveStatus.UP, 90));

        mockMvc.perform(post("/api/v1/public/business-services/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "service_code": "order-api",
                                  "service_name": "订单服务",
                                  "instance_id": "order-api-1",
                                  "status": "UP",
                                  "management_url": "http://10.0.0.8:8080/actuator",
                                  "metadata": {"region": "cn-east"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.service_code").value("order-api"))
                .andExpect(jsonPath("$.data.instance_id").value("order-api-1"))
                .andExpect(jsonPath("$.data.registered").value(true))
                .andExpect(jsonPath("$.data.effective_status").value("UP"))
                .andExpect(jsonPath("$.data.offline_after_seconds").value(90));

        ArgumentCaptor<BusinessServiceHeartbeatDto> captor =
                ArgumentCaptor.forClass(BusinessServiceHeartbeatDto.class);
        verify(registryService).reportHeartbeat(captor.capture(), anyString());
        assertThat(captor.getValue().getServiceCode()).isEqualTo("order-api");
        assertThat(captor.getValue().getManagementUrl())
                .isEqualTo("http://10.0.0.8:8080/actuator");
        assertThat(captor.getValue().getMetadata()).containsEntry("region", "cn-east");
    }

    @Test
    void missingRequiredFieldReturnsBusinessAndHttp400() throws Exception {
        mockMvc.perform(post("/api/v1/public/business-services/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "service_code": "order-api",
                                  "service_name": "订单服务",
                                  "instance_id": "order-api-1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.msg").value("服务状态不能为空"));
    }

    @Test
    void invalidEventSeverityIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/public/business-services/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_id": "evt-1",
                                  "service_code": "order-api",
                                  "instance_id": "order-api-1",
                                  "event_type": "DEPLOYMENT",
                                  "severity": "FATAL",
                                  "title": "发布完成"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.msg").value("事件级别不能为空"));
    }

    @Test
    void managementInstanceQueryBindsFiltersAndCapsPaginationInService() throws Exception {
        when(registryService.getInstancePage(any(BusinessServiceInstanceSearchDto.class)))
                .thenReturn(new PageRowsVo<>(List.of(), 0));

        mockMvc.perform(get("/api/v1/system/business-services/instances")
                        .param("page", "2")
                        .param("per_page", "50")
                        .param("keyword", "order")
                        .param("environment", "prod")
                        .param("status", "OFFLINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        ArgumentCaptor<BusinessServiceInstanceSearchDto> captor =
                ArgumentCaptor.forClass(BusinessServiceInstanceSearchDto.class);
        verify(registryService).getInstancePage(captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(2);
        assertThat(captor.getValue().getPerPage()).isEqualTo(50);
        assertThat(captor.getValue().getKeyword()).isEqualTo("order");
        assertThat(captor.getValue().getEnvironment()).isEqualTo("prod");
        assertThat(captor.getValue().getStatus()).isEqualTo(BusinessServiceEffectiveStatus.OFFLINE);
    }
}
