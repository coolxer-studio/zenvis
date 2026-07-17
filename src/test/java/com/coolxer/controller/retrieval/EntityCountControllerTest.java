package com.coolxer.controller.retrieval;

import com.coolxer.service.retrieval.EntityCoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EntityCountControllerTest {

    private EntityCoreService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(EntityCoreService.class);
        EntityCountController controller = new EntityCountController();
        ReflectionTestUtils.setField(controller, "entityCoreService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void ipStatisticsSupportsCommaSeparatedEntities() throws Exception {
        when(service.ipStatistics(List.of("traffic", "domain"), "192.0.2.1"))
                .thenReturn(Map.of("ip", "192.0.2.1", "total", 3L));

        mockMvc.perform(get("/api/v1/entity/ip-statistics")
                        .param("entities", "traffic,domain")
                        .param("ip", "192.0.2.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.ip").value("192.0.2.1"))
                .andExpect(jsonPath("$.data.total").value(3));

        verify(service).ipStatistics(List.of("traffic", "domain"), "192.0.2.1");
    }

    @Test
    void ipStatisticsSupportsRepeatedEntityParameters() throws Exception {
        when(service.ipStatistics(List.of("traffic", "domain"), "2001:db8::1"))
                .thenReturn(Map.of("ip", "2001:db8::1", "total", 2L));

        mockMvc.perform(get("/api/v1/entity/ip-statistics")
                        .param("entities", "traffic", "domain")
                        .param("ip", "2001:db8::1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0));

        verify(service).ipStatistics(List.of("traffic", "domain"), "2001:db8::1");
    }
}
