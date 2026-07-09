package com.coolxer.service.dih;

import com.coolxer.model.dih.ChatMessagePart;
import com.coolxer.service.dih.agent.AnalysisAgent;
import com.coolxer.service.dih.agent.DataAccessAgent;
import com.coolxer.service.dih.agent.DataVisualizationAgent;
import com.coolxer.service.dih.agent.DisposeAgent;
import com.coolxer.service.dih.agent.ReportAgent;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.mcp.AgentMcpToolService;
import com.coolxer.service.system.DashboardService;
import com.coolxer.service.system.MenuService;
import com.coolxer.service.system.PushTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DihChatApplicationServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void buildStructuredExtraDataPatchIncludesDataVisualizationChartLibrary() {
        DihChatApplicationService service = new DihChatApplicationService(
                null,
                null,
                null,
                null,
                null,
                null,
                (AnalysisAgent) null,
                (DisposeAgent) null,
                (ReportAgent) null,
                (DataAccessAgent) null,
                (DataVisualizationAgent) null,
                null,
                null,
                null,
                (AgentMcpToolService) null,
                (SkillService) null,
                null,
                (PushTaskService) null,
                (DashboardService) null,
                (MenuService) null
        );

        ChatMessagePart part = ChatMessagePart.builder()
                .type("visualization-chart-record")
                .content("登录趋势图")
                .metadata(Map.of(
                        "id", "login-trend",
                        "name", "登录趋势图",
                        "chartType", "line",
                        "entity", "user_event",
                        "api", "/api/v1/entity/user_event/list"
                ))
                .build();

        Map<String, Object> patch = ReflectionTestUtils.invokeMethod(
                service,
                "buildStructuredExtraDataPatch",
                List.of(part)
        );

        assertThat(patch).isNotNull();
        Map<String, Object> dataVisualization = (Map<String, Object>) patch.get("dataVisualization");
        assertThat(dataVisualization).isNotNull();
        List<Map<String, Object>> chartLibrary = (List<Map<String, Object>>) dataVisualization.get("chartLibrary");
        assertThat(chartLibrary).hasSize(1);
        assertThat(chartLibrary.get(0))
                .containsEntry("id", "login-trend")
                .containsEntry("name", "登录趋势图")
                .containsEntry("chartType", "line")
                .containsEntry("entity", "user_event");
    }
}
