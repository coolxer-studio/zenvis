package com.coolxer.service.dih;

import com.coolxer.dao.mysql.entity.ChatSession;
import com.coolxer.dao.mysql.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class DisposeDemoResponseService {

    public static final String DISPOSE_DEMO_TITLE = "策略控制演示";
    public static final String DISPOSE_WEBSHELL_EXAMPLE_PROMPT =
            "请基于 WebShell 高危研判结果生成一条处置策略控制演示，要求先生成策略记录，再进入试验场验证，验证成功后再下发正式生效。";

    private static final int DEMO_STREAM_CHUNK_SIZE = 22;
    private static final Duration DEMO_STREAM_DELAY = Duration.ofMillis(45);
    private static final String ACTION_CONFIRM_TRIAL = "policy_demo.confirm_trial";
    private static final String ACTION_CONFIRM_RETRY_TRIAL = "policy_demo.confirm_retry_trial";
    private static final String ACTION_CONFIRM_APPLY = "policy_demo.confirm_apply";

    private static final String CONFIG_GENERATED_RESPONSE = """
            已完成第一阶段：根据策略控制需求生成处置策略配置，并记录到右侧策略记录 tab。你可以先进入试验场验证，也可以继续补充更新策略。

            ```zenvis:disposal-policy-config
            [
              {
                "name": "webshell_high_risk_isolate",
                "tag": "webshell_high_risk",
                "sourceRegex": "web-01|10\\\\.106\\\\.108\\\\.110",
                "action": {
                  "type": "isolate_host",
                  "title": "隔离 WebShell 高危主机",
                  "params": {
                    "host": "web-01",
                    "preserveEvidence": true
                  }
                }
              }
            ]
            ```

            ```zenvis:policy-record
            {
              "recordId": "demo-policy-webshell-disposal-v1",
              "policyType": "disposal",
              "changeDescription": "新增 WebShell 高危告警处置策略，命中 webshell_high_risk 标签后隔离 web-01 并保留现场证据。",
              "changeMode": "add",
              "configType": "punish",
              "fileName": "webshell-high-risk-isolate.json",
              "oldConfig": "",
              "newConfig": [
                {
                  "name": "webshell_high_risk_isolate",
                  "tag": "webshell_high_risk",
                  "sourceRegex": "web-01|10\\\\.106\\\\.108\\\\.110",
                  "action": {
                    "type": "isolate_host",
                    "title": "隔离 WebShell 高危主机",
                    "params": {
                      "host": "web-01",
                      "preserveEvidence": true
                    }
                  }
                }
              ],
              "validationStatus": "unverified",
              "effectiveStatus": "no",
              "updatedAt": "2026-07-13 11:00:00"
            }
            ```

            ```zenvis:confirm
            {"title":"是否进入试验场验证","content":"策略记录已生成。确认后会将当前策略推给试验场做验证；如需调整，可补充更新要求。","action":"policy_demo.confirm_trial","actions":["approved","revise","rejected"],"reviseLabel":"继续补充更新"}
            ```
            """;

    private static final String CONFIG_REVISED_RESPONSE = """
            已根据补充要求更新策略配置，并生成新的策略记录。请确认是否进入试验场验证。

            ```zenvis:policy-record
            {
              "recordId": "demo-policy-webshell-disposal-v1-revised",
              "policyType": "disposal",
              "changeDescription": "补充阻断异常外联地址，并保留主机隔离前置确认。",
              "changeMode": "modify",
              "configType": "punish",
              "fileName": "webshell-high-risk-isolate.json",
              "oldConfig": [
                {
                  "name": "webshell_high_risk_isolate",
                  "tag": "webshell_high_risk",
                  "sourceRegex": "web-01|10\\\\.106\\\\.108\\\\.110",
                  "action": {"type":"isolate_host","title":"隔离 WebShell 高危主机"}
                }
              ],
              "newConfig": [
                {
                  "name": "webshell_high_risk_isolate_and_block",
                  "tag": "webshell_high_risk",
                  "sourceRegex": "web-01|10\\\\.106\\\\.108\\\\.110|172\\\\.16\\\\.8\\\\.45",
                  "action": {
                    "type": "isolate_and_block",
                    "title": "隔离主机并阻断异常外联",
                    "params": {"host":"web-01","blockIp":"172.16.8.45","requireConfirm":true}
                  }
                }
              ],
              "validationStatus": "unverified",
              "effectiveStatus": "no",
              "updatedAt": "2026-07-13 11:01:00"
            }
            ```

            ```zenvis:confirm
            {"title":"是否进入试验场验证","content":"策略已补充更新。确认后推送到试验场验证；如仍需调整，可继续补充。","action":"policy_demo.confirm_trial","actions":["approved","revise","rejected"],"reviseLabel":"继续补充更新"}
            ```
            """;

    private static final String TRIAL_FAILED_RESPONSE = """
            试验场验证未通过：样例数据中的 source 为 `web-01/one.jsp`，原策略 sourceRegex 未覆盖 URL 路径，处置规则未命中。已回到第一阶段生成修复后的策略记录，请重新试验。

            ```zenvis:policy-record
            {
              "recordId": "demo-policy-webshell-disposal-v1",
              "policyType": "disposal",
              "changeDescription": "新增 WebShell 高危告警处置策略，试验场未命中 URL 路径样例。",
              "changeMode": "add",
              "configType": "punish",
              "fileName": "webshell-high-risk-isolate.json",
              "oldConfig": "",
              "newConfig": [
                {
                  "name": "webshell_high_risk_isolate",
                  "tag": "webshell_high_risk",
                  "sourceRegex": "web-01|10\\\\.106\\\\.108\\\\.110",
                  "action": {"type":"isolate_host","title":"隔离 WebShell 高危主机"}
                }
              ],
              "validationStatus": "failed",
              "effectiveStatus": "no",
              "trialResult": {
                "passed": false,
                "matchedRules": [],
                "warnings": ["sourceRegex 未覆盖 /one.jsp URL 样例"],
                "suggestions": ["将 sourceRegex 扩展到 one.jsp 或 WebShell URL 路径"]
              },
              "updatedAt": "2026-07-13 11:02:00"
            }
            ```

            ```zenvis:policy-record
            {
              "recordId": "demo-policy-webshell-disposal-v2",
              "policyType": "disposal",
              "changeDescription": "修复 WebShell 处置策略的来源匹配范围，新增 one.jsp URL 路径命中条件。",
              "changeMode": "modify",
              "configType": "punish",
              "fileName": "webshell-high-risk-isolate.json",
              "oldConfig": [
                {
                  "name": "webshell_high_risk_isolate",
                  "tag": "webshell_high_risk",
                  "sourceRegex": "web-01|10\\\\.106\\\\.108\\\\.110",
                  "action": {"type":"isolate_host","title":"隔离 WebShell 高危主机"}
                }
              ],
              "newConfig": [
                {
                  "name": "webshell_high_risk_isolate",
                  "tag": "webshell_high_risk",
                  "sourceRegex": "web-01|10\\\\.106\\\\.108\\\\.110|one\\\\.jsp",
                  "action": {
                    "type": "isolate_host",
                    "title": "隔离 WebShell 高危主机",
                    "params": {"host":"web-01","preserveEvidence":true}
                  }
                }
              ],
              "validationStatus": "unverified",
              "effectiveStatus": "no",
              "updatedAt": "2026-07-13 11:03:00"
            }
            ```

            ```zenvis:confirm
            {"title":"修复后的策略是否重新试验","content":"已根据失败原因生成修复后的策略记录。确认后将修复后的策略推送到试验场重新验证。","action":"policy_demo.confirm_retry_trial","actions":["approved","revise","rejected"],"reviseLabel":"继续补充更新"}
            ```
            """;

    private static final String TRIAL_SUCCESS_RESPONSE = """
            试验场验证成功：样例数据命中 WebShell 高危标签和 one.jsp URL 路径，处置动作可触发。请确认是否下发到系统正式生效。

            ```zenvis:policy-record
            {
              "recordId": "demo-policy-webshell-disposal-v2",
              "policyType": "disposal",
              "changeDescription": "修复 WebShell 处置策略的来源匹配范围，试验场验证成功。",
              "changeMode": "modify",
              "configType": "punish",
              "fileName": "webshell-high-risk-isolate.json",
              "oldConfig": [
                {
                  "name": "webshell_high_risk_isolate",
                  "tag": "webshell_high_risk",
                  "sourceRegex": "web-01|10\\\\.106\\\\.108\\\\.110",
                  "action": {"type":"isolate_host","title":"隔离 WebShell 高危主机"}
                }
              ],
              "newConfig": [
                {
                  "name": "webshell_high_risk_isolate",
                  "tag": "webshell_high_risk",
                  "sourceRegex": "web-01|10\\\\.106\\\\.108\\\\.110|one\\\\.jsp",
                  "action": {
                    "type": "isolate_host",
                    "title": "隔离 WebShell 高危主机",
                    "params": {"host":"web-01","preserveEvidence":true}
                  }
                }
              ],
              "validationStatus": "success",
              "effectiveStatus": "no",
              "trialResult": {
                "passed": true,
                "matchedRules": ["webshell_high_risk -> 隔离 WebShell 高危主机"],
                "warnings": [],
                "suggestions": []
              },
              "updatedAt": "2026-07-13 11:04:00"
            }
            ```

            ```zenvis:confirm
            {"title":"是否下发策略到系统生效","content":"策略已通过试验场验证。确认后会写入系统并正式生效。","action":"policy_demo.confirm_apply","level":"warning","actions":["approved","rejected"]}
            ```
            """;

    private static final String APPLY_RESPONSE = """
            已完成第三阶段：策略已下发到系统并正式生效。

            ```zenvis:policy-record
            {
              "recordId": "demo-policy-webshell-disposal-v2",
              "policyType": "disposal",
              "changeDescription": "WebShell 高危处置策略已通过试验并下发到系统正式生效。",
              "changeMode": "modify",
              "configType": "punish",
              "fileName": "webshell-high-risk-isolate.json",
              "oldConfig": [
                {
                  "name": "webshell_high_risk_isolate",
                  "tag": "webshell_high_risk",
                  "sourceRegex": "web-01|10\\\\.106\\\\.108\\\\.110",
                  "action": {"type":"isolate_host","title":"隔离 WebShell 高危主机"}
                }
              ],
              "newConfig": [
                {
                  "name": "webshell_high_risk_isolate",
                  "tag": "webshell_high_risk",
                  "sourceRegex": "web-01|10\\\\.106\\\\.108\\\\.110|one\\\\.jsp",
                  "action": {
                    "type": "isolate_host",
                    "title": "隔离 WebShell 高危主机",
                    "params": {"host":"web-01","preserveEvidence":true}
                  }
                }
              ],
              "validationStatus": "success",
              "effectiveStatus": "yes",
              "applyResult": {
                "applied": true,
                "message": "已写入 punish_config/webshell-high-risk-isolate.json 并完成应用"
              },
              "updatedAt": "2026-07-13 11:05:00"
            }
            ```
            """;

    private static final String DEMO_CANCEL_RESPONSE = """
            ```zenvis:notice
            {"title":"策略控制演示已暂停","content":"已按你的选择暂停当前策略控制演示流程，未进入下一阶段。","level":"info"}
            ```
            """;

    public Optional<Flux<String>> findResponse(ChatSession chatSession, String chatId, String prompt, User user) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }
        return findDemoResponse(prompt.trim()).map(this::streamResponse);
    }

    private Optional<String> findDemoResponse(String prompt) {
        if (isDisposeDemoPrompt(prompt)) {
            return Optional.of(CONFIG_GENERATED_RESPONSE);
        }
        if (isTrialPrompt(prompt)) {
            return Optional.of(TRIAL_FAILED_RESPONSE);
        }
        if (isRetryTrialPrompt(prompt)) {
            return Optional.of(TRIAL_SUCCESS_RESPONSE);
        }
        if (isRevisePrompt(prompt)) {
            return Optional.of(CONFIG_REVISED_RESPONSE);
        }
        if (isApplyPrompt(prompt)) {
            return Optional.of(APPLY_RESPONSE);
        }
        if (isCancelPrompt(prompt)) {
            return Optional.of(DEMO_CANCEL_RESPONSE);
        }
        return Optional.empty();
    }

    private boolean isTrialPrompt(String prompt) {
        return prompt.contains("我已确认进入试验场验证")
                || prompt.contains(ACTION_CONFIRM_TRIAL);
    }

    private boolean isRetryTrialPrompt(String prompt) {
        return prompt.contains("我已确认重新进入试验场验证")
                || prompt.contains(ACTION_CONFIRM_RETRY_TRIAL);
    }

    private boolean isRevisePrompt(String prompt) {
        return prompt.contains("我需要补充更新策略配置")
                || prompt.contains("已补充策略更新要求");
    }

    private boolean isApplyPrompt(String prompt) {
        return prompt.contains("我已确认下发策略到系统正式生效")
                || prompt.contains(ACTION_CONFIRM_APPLY);
    }

    private boolean isCancelPrompt(String prompt) {
        return prompt.contains("我已取消策略控制演示")
                || prompt.contains("取消策略控制演示");
    }

    public static boolean isDisposeDemoPrompt(String prompt) {
        return StringUtils.hasText(prompt) && DISPOSE_WEBSHELL_EXAMPLE_PROMPT.equals(prompt.trim());
    }

    private Flux<String> streamResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return Flux.just("");
        }
        return Flux.fromIterable(splitResponseChunks(response))
                .delayElements(DEMO_STREAM_DELAY);
    }

    private List<String> splitResponseChunks(String response) {
        List<String> chunks = new java.util.ArrayList<>();
        int index = 0;
        while (index < response.length()) {
            int limit = Math.min(response.length(), index + DEMO_STREAM_CHUNK_SIZE);
            chunks.add(response.substring(index, limit));
            index = limit;
        }
        return chunks;
    }
}
