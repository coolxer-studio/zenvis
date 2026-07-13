package com.coolxer.service.dih;

import org.junit.jupiter.api.Test;

import static com.coolxer.service.dih.DisposeDemoResponseService.DISPOSE_WEBSHELL_EXAMPLE_PROMPT;
import static org.assertj.core.api.Assertions.assertThat;

class DisposeDemoResponseServiceTest {

    private final DisposeDemoResponseService service = new DisposeDemoResponseService();

    @Test
    void demoPromptReturnsPolicyRecordOnly() {
        String response = String.join("", service.findResponse(null, "chat", DISPOSE_WEBSHELL_EXAMPLE_PROMPT, null)
                .orElseThrow()
                .collectList()
                .block());

        assertThat(response)
                .contains("zenvis:policy-record")
                .contains("\"validationStatus\": \"unverified\"")
                .contains("\"effectiveStatus\": \"no\"")
                .contains("policy_demo.confirm_trial")
                .doesNotContain("policy_demo.confirm_apply");
    }

    @Test
    void trialPromptReturnsFailureAndFixedRecord() {
        String response = String.join("", service.findResponse(null, "chat", "我已确认进入试验场验证", null)
                .orElseThrow()
                .collectList()
                .block());

        assertThat(response)
                .contains("\"validationStatus\": \"failed\"")
                .contains("demo-policy-webshell-disposal-v2")
                .contains("policy_demo.confirm_retry_trial");
    }

    @Test
    void retryTrialPromptReturnsSuccessAndApplyConfirm() {
        String response = String.join("", service.findResponse(null, "chat", "我已确认重新进入试验场验证", null)
                .orElseThrow()
                .collectList()
                .block());

        assertThat(response)
                .contains("\"validationStatus\": \"success\"")
                .contains("\"effectiveStatus\": \"no\"")
                .contains("policy_demo.confirm_apply");
    }

    @Test
    void applyPromptReturnsEffectiveRecord() {
        String response = String.join("", service.findResponse(null, "chat", "我已确认下发策略到系统正式生效", null)
                .orElseThrow()
                .collectList()
                .block());

        assertThat(response)
                .contains("\"validationStatus\": \"success\"")
                .contains("\"effectiveStatus\": \"yes\"")
                .contains("\"applied\": true");
    }
}
