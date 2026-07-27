package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigManagementDemoResponseServiceTest {

    private final ConfigManagementDemoResponseService service = new ConfigManagementDemoResponseService();

    @Test
    void examplePromptReturnsBuiltinConfigurationAndTrialConfirmation() {
        String response = responseOf(service.findResponse(
                null,
                "chat-1",
                ConfigManagementDemoResponseService.CONFIG_MANAGEMENT_EXAMPLE_PROMPT,
                null
        ));

        assertThat(response)
                .contains("zenvis:config-record")
                .contains("\"recordId\": \"demo-config-system-info-001\"")
                .contains("\"validationStatus\": \"unverified\"")
                .contains("\"action\":\"config.confirm_trial\"");
    }

    @Test
    void demoConfirmationsReturnBuiltinValidationAndApplyResults() {
        ChatSession session = new ChatSession()
                .setTitle(ConfigManagementDemoResponseService.CONFIG_MANAGEMENT_DEMO_TITLE);

        String validation = responseOf(service.findResponse(
                session,
                "chat-1",
                "我已确认进入试验场验证。",
                null
        ));
        String apply = responseOf(service.findResponse(
                session,
                "chat-1",
                "我已确认将验证成功的配置正式下发。",
                null
        ));

        assertThat(validation)
                .contains("\"validationStatus\": \"success\"")
                .contains("\"source\": \"builtin_demo\"")
                .contains("\"action\":\"config.confirm_apply\"");
        assertThat(apply)
                .contains("\"effectiveStatus\": \"yes\"")
                .contains("\"approvalStatus\": \"approved\"")
                .contains("\"actualSystemChanged\": false")
                .contains("实际系统配置未被修改");
    }

    @Test
    void nonExamplePromptIsNotIntercepted() {
        assertThat(service.findResponse(null, "chat-1", "修改订单系统配置", null)).isEmpty();
    }

    private String responseOf(java.util.Optional<reactor.core.publisher.Flux<String>> response) {
        assertThat(response).isPresent();
        return String.join("", response.orElseThrow().collectList().block());
    }
}
