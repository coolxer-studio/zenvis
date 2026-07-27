package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.dih.Message;
import com.coolxer.utils.JacksonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 配置管理中性示例的内置三阶段响应，不调用模型或 MCP，也不修改实际系统配置。
 */
@Slf4j
@Service
public class ConfigManagementDemoResponseService {

    public static final String CONFIG_MANAGEMENT_DEMO_TITLE = "系统信息展示配置演示";
    public static final String CONFIG_MANAGEMENT_EXAMPLE_PROMPT =
            "请调整系统信息展示配置：将系统标题改为“ZenVis 数据服务中心”，并更新产品介绍；生成配置记录后进入试验场验证，我确认后再正式生效。";

    private static final int DEMO_STREAM_CHUNK_SIZE = 64;
    private static final Duration DEMO_STREAM_DELAY = Duration.ofMillis(8);

    private static final String OLD_CONFIG = """
            {
              "system_title": "ZenVis",
              "product_name": "ZenVis",
              "product_version": "1.0.0",
              "product_introduction": "基于配置的数据服务与可视化平台",
              "service_phone": "400-000-0000",
              "service_email": "service@example.com",
              "technical_email": "support@example.com",
              "copyright": "Copyright 2026 ZenVis"
            }
            """;

    private static final String NEW_CONFIG = """
            {
              "system_title": "ZenVis 数据服务中心",
              "product_name": "ZenVis",
              "product_version": "1.0.0",
              "product_introduction": "面向业务的数据接入、分析、可视化与配置管理平台",
              "service_phone": "400-000-0000",
              "service_email": "service@example.com",
              "technical_email": "support@example.com",
              "copyright": "Copyright 2026 ZenVis"
            }
            """;

    public Optional<Flux<String>> findResponse(ChatSession chatSession, String chatId, String prompt, User user) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }
        String normalizedPrompt = prompt.trim();
        if (isConfigManagementDemoPrompt(normalizedPrompt)) {
            return Optional.of(streamResponse(buildConfigGenerationResponse()));
        }
        if (!isDemoSession(chatSession)) {
            return Optional.empty();
        }
        if (isCancelPrompt(normalizedPrompt)) {
            return Optional.of(streamResponse(buildCancelledResponse()));
        }
        if (isRevisePrompt(normalizedPrompt)) {
            return Optional.of(streamResponse(buildFixedDemoNotice()));
        }
        if (isApplyConfirmation(normalizedPrompt)) {
            return Optional.of(streamResponse(buildApplyResponse()));
        }
        if (isTrialConfirmation(normalizedPrompt)) {
            return Optional.of(streamResponse(buildValidationResponse()));
        }
        return Optional.of(streamResponse(buildFixedDemoNotice()));
    }

    public static boolean isConfigManagementDemoPrompt(String prompt) {
        return StringUtils.hasText(prompt) && CONFIG_MANAGEMENT_EXAMPLE_PROMPT.equals(prompt.trim());
    }

    private boolean isTrialConfirmation(String prompt) {
        return prompt.contains("我已确认进入试验场验证")
                || prompt.contains("推送到试验场验证")
                || prompt.contains("config.confirm_trial");
    }

    private boolean isApplyConfirmation(String prompt) {
        return prompt.contains("我已确认将验证成功的配置正式下发")
                || prompt.contains("我已确认将配置正式下发到系统生效")
                || prompt.contains("下发到系统正式生效")
                || prompt.contains("config.confirm_apply");
    }

    private boolean isCancelPrompt(String prompt) {
        return prompt.contains("我已取消当前配置管理流程")
                || prompt.contains("不要进入下一阶段");
    }

    private boolean isRevisePrompt(String prompt) {
        return prompt.contains("我需要调整配置")
                || prompt.contains("已补充配置调整要求");
    }

    private boolean isDemoSession(ChatSession chatSession) {
        if (chatSession == null) {
            return false;
        }
        if (CONFIG_MANAGEMENT_DEMO_TITLE.equals(chatSession.getTitle())) {
            return true;
        }
        if (StringUtils.hasText(chatSession.getExtraData())
                && chatSession.getExtraData().contains("demo-config-system-info-001")) {
            return true;
        }
        if (!StringUtils.hasText(chatSession.getMessages())) {
            return false;
        }
        try {
            List<Message> messages = JacksonUtil.toList(
                    chatSession.getMessages(),
                    new TypeReference<List<Message>>() {
                    }
            );
            return messages.stream()
                    .map(Message::getContent)
                    .filter(StringUtils::hasText)
                    .anyMatch(content -> isConfigManagementDemoPrompt(content)
                            || content.contains("demo-config-system-info-001"));
        } catch (Exception e) {
            log.warn("判断配置管理演示会话失败: {}", e.getMessage());
            return false;
        }
    }

    private Flux<String> streamResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return Flux.just("");
        }
        List<String> chunks = new ArrayList<>();
        int index = 0;
        while (index < response.length()) {
            int end = Math.min(response.length(), index + DEMO_STREAM_CHUNK_SIZE);
            chunks.add(response.substring(index, end));
            index = end;
        }
        return Flux.fromIterable(chunks).delayElements(DEMO_STREAM_DELAY);
    }

    private String buildConfigGenerationResponse() {
        return """
                已直接加载内置系统信息配置演示结果。本阶段不会读取或修改实际系统配置。

                ```zenvis:config-record
                {
                  "recordId": "demo-config-system-info-001",
                  "changeDescription": "调整系统标题和产品介绍",
                  "changeMode": "modify",
                  "configType": "web",
                  "fileName": "system_info.json",
                  "format": "json",
                  "oldConfig": %s,
                  "newConfig": %s,
                  "validationStatus": "unverified",
                  "effectiveStatus": "no",
                  "validationResult": {},
                  "applyResult": {},
                  "updatedAt": "2026-07-27T20:00:00+08:00"
                }
                ```

                ```zenvis:confirm
                {"title":"演示配置已生成，是否进入试验场","content":"确认后将加载演示格式与 schema 验证结果。","action":"config.confirm_trial","actions":["approved","revise","rejected"],"reviseLabel":"调整配置"}
                ```
                """.formatted(OLD_CONFIG.trim(), NEW_CONFIG.trim());
    }

    private String buildValidationResponse() {
        return """
                已加载演示试验场验证结果。

                ```zenvis:config-record
                {
                  "recordId": "demo-config-system-info-001",
                  "changeDescription": "调整系统标题和产品介绍",
                  "changeMode": "modify",
                  "configType": "web",
                  "fileName": "system_info.json",
                  "format": "json",
                  "oldConfig": %s,
                  "newConfig": %s,
                  "validationStatus": "success",
                  "effectiveStatus": "no",
                  "validationResult": {
                    "source": "builtin_demo",
                    "status": "success",
                    "checks": [
                      {"name":"JSON 语法","status":"success"},
                      {"name":"system_info schema","status":"success"},
                      {"name":"展示字段完整性","status":"success"}
                    ]
                  },
                  "applyResult": {},
                  "updatedAt": "2026-07-27T20:01:00+08:00"
                }
                ```

                ```zenvis:confirm
                {"title":"演示配置验证成功，是否模拟正式生效","content":"确认后仅在内置演示范围模拟审批、写入和读回，不修改实际系统配置。","action":"config.confirm_apply","level":"warning","actions":["approved","rejected"]}
                ```
                """.formatted(OLD_CONFIG.trim(), NEW_CONFIG.trim());
    }

    private String buildApplyResponse() {
        return """
                已在内置演示范围完成模拟审批、写入和读回。实际系统配置未被修改。

                ```zenvis:config-record
                {
                  "recordId": "demo-config-system-info-001",
                  "changeDescription": "调整系统标题和产品介绍",
                  "changeMode": "modify",
                  "configType": "web",
                  "fileName": "system_info.json",
                  "format": "json",
                  "oldConfig": %s,
                  "newConfig": %s,
                  "validationStatus": "success",
                  "effectiveStatus": "yes",
                  "validationResult": {
                    "source": "builtin_demo",
                    "status": "success"
                  },
                  "applyResult": {
                    "source": "builtin_demo",
                    "scope": "demo_only",
                    "approvalStatus": "approved",
                    "writeSucceeded": true,
                    "readBackMatched": true,
                    "actualSystemChanged": false
                  },
                  "updatedAt": "2026-07-27T20:02:00+08:00"
                }
                ```

                ```zenvis:notice
                {"title":"配置管理演示已完成","content":"右侧记录展示演示范围内的生效状态；本流程未修改实际系统配置。","level":"success"}
                ```
                """.formatted(OLD_CONFIG.trim(), NEW_CONFIG.trim());
    }

    private String buildCancelledResponse() {
        return """
                已结束本次配置管理演示，不会进入下一阶段，也不会修改实际系统配置。

                ```zenvis:notice
                {"title":"配置管理演示已取消","content":"当前会话保留已生成的内置演示记录。","level":"info"}
                ```
                """;
    }

    private String buildFixedDemoNotice() {
        return """
                当前会话展示预设的配置管理演示流程。

                ```zenvis:notice
                {"title":"演示流程不支持自定义调整","content":"请使用确认卡继续演示流程；如需管理真实配置，请新建配置管理会话。","level":"info"}
                ```
                """;
    }
}
