package com.coolxer.controller.system;

import com.coolxer.service.system.PluginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PluginControllerTest {

    private PluginService pluginService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        pluginService = mock(PluginService.class);
        PluginController controller = new PluginController();
        ReflectionTestUtils.setField(controller, "pluginService", pluginService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void exposesUpgradeWithServerParsedPackagePath() throws Exception {
        mockMvc.perform(post("/api/v1/system/plugin/7/upgrade")
                        .contentType("application/json")
                        .content("{\"plugin_path\":\"/plugins/temp/demo.tar.gz\"}"))
                .andExpect(status().isOk());

        verify(pluginService).upgrade(eq(7L), any());
    }

    @Test
    void oldUpdateAndBulkUpdateRoutesAreRemoved() throws Exception {
        mockMvc.perform(post("/api/v1/system/plugin/7/update")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/system/plugin/7,8/bulk-update")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
