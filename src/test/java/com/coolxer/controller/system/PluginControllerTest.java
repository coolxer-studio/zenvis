package com.coolxer.controller.system;

import com.coolxer.service.system.PluginService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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

    @Test
    void pluginLogStreamOutlivesMcpConnectionChecks() throws Exception {
        PluginController controller = new PluginController();
        ReflectionTestUtils.setField(controller, "pluginService", pluginService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        AsyncContext asyncContext = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(asyncContext);
        when(pluginService.getLogs(7L)).thenReturn("8 存储MCP服务配置......", "完成......");

        ResponseEntity<StreamingResponseBody> response = controller.handleLog(7L, request);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        verify(asyncContext).setTimeout(Duration.ofMinutes(30).toMillis());
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("8 存储MCP服务配置......")
                .contains("完成......");
    }
}
