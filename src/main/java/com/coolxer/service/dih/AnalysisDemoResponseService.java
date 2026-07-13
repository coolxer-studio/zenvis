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
public class AnalysisDemoResponseService {

    public static final String ANALYSIS_DEMO_TITLE = "研判分析演示";
    public static final String ANALYSIS_WEB_SHELL_EXAMPLE_PROMPT = """
            请基于这条 WebShell 异常访问告警进行一次完整研判分析演示。

            # 演示告警日志信息

            ```json
            {
              "alarmId": "ALM-20260713-0007",
              "alarmName": "WebShell 异常访问",
              "alarmLevel": "高危",
              "alarmTime": "2026-07-13 09:56:11",
              "sourceIp": "10.108.108.23",
              "destIp": "10.106.108.110",
              "destPort": 8080,
              "targetHost": "web-01",
              "targetAsset": "生产 Web 应用服务器",
              "url": "http://10.106.108.110:8080/one.jsp",
              "method": "POST",
              "ruleName": "WebShell 命令执行疑似行为",
              "ruleHit": "JSP 页面接收疑似命令执行参数，且 Web 服务进程出现异常子进程",
              "rawLog": {
                "time": "2026-07-13 09:56:11",
                "host": "web-01",
                "server": "tomcat",
                "request": "POST /one.jsp HTTP/1.1",
                "status": 200,
                "userAgent": "Mozilla/5.0",
                "bodyDigest": "cmd=whoami&exec=1",
                "message": "同源 IP 对 one.jsp 发起 POST 请求，请关联近 10 分钟内访问日志、进程日志、网络连接日志和规则命中日志完成研判。"
              }
            }
            ```
            """.strip();

    private static final int DEMO_STREAM_CHUNK_SIZE = 22;
    private static final Duration DEMO_STREAM_DELAY = Duration.ofMillis(45);
    private static final String ACTION_CONFIRM_LOG_AGGREGATION = "analysis_demo.confirm_log_aggregation";
    private static final String ACTION_CONFIRM_SANDBOX_RESULT = "analysis_demo.confirm_sandbox_result";

    private static final String LOG_AGGREGATION_RESPONSE = """
            已完成第一阶段：日志聚合。请确认聚合结果是否可以进入沙箱研判；如还需要补充更多数据，可以选择补充信息继续聚合。

            ```zenvis:analysis-record
            {
              "recordId": "demo-analysis-log-aggregation",
              "stage": "log_aggregation",
              "status": "completed",
              "title": "日志聚合完成",
              "content": "围绕 WebShell 异常访问告警，已关联访问日志、进程行为、登录事件和策略命中记录。",
              "startedAt": "2026-07-13 10:00:00",
              "completedAt": "2026-07-13 10:00:18",
              "alarm": {
                "alarmId": "ALM-20260713-0007",
                "name": "WebShell 异常访问",
                "level": "高危",
                "targetHost": "web-01",
                "sourceIp": "10.108.108.23",
                "destIp": "10.106.108.110",
                "path": "/one.jsp"
              },
              "evidenceCount": 6,
              "toolNames": ["retrieval_search", "entity_list"],
              "logs": [
                {"id":"log-001","time":"2026-07-13 09:56:11","type":"http_access","level":"warning","sourceIp":"10.108.108.23","destIp":"10.106.108.110","host":"web-01","method":"POST","path":"/one.jsp","status":200,"message":"同源 IP 对 one.jsp 发起 POST 请求"},
                {"id":"log-002","time":"2026-07-13 09:57:04","type":"http_access","level":"warning","sourceIp":"10.108.108.23","destIp":"10.106.108.110","host":"web-01","method":"POST","path":"/one.jsp","status":200,"message":"请求体包含疑似命令执行参数"},
                {"id":"log-003","time":"2026-07-13 09:58:22","type":"process","level":"high","host":"web-01","process":"cmd.exe","parentProcess":"java.exe","commandLine":"cmd.exe /c whoami","message":"Web 服务进程派生命令行进程"},
                {"id":"log-004","time":"2026-07-13 09:59:10","type":"network","level":"medium","host":"web-01","process":"java.exe","destIp":"172.16.8.45","destPort":4444,"message":"Web 服务进程出现异常外联"},
                {"id":"log-005","time":"2026-07-13 10:00:03","type":"login","level":"info","host":"web-01","account":"svc-web","sourceIp":"10.108.108.23","message":"告警源 IP 关联服务账号访问记录"},
                {"id":"log-006","time":"2026-07-13 10:00:12","type":"rule_hit","level":"high","rule":"webshell_command_execution","message":"命中 WebShell 命令执行检测规则"}
              ]
            }
            ```

            ```zenvis:confirm
            {"title":"日志聚合已完成，是否进入沙箱研判","content":"已围绕当前 WebShell 告警聚合 6 条相关日志。确认后进入独立沙箱研判阶段；如证据不足，可补充更多数据后重新聚合。","action":"__ACTION__","actions":["approved","revise","rejected"],"reviseLabel":"补充更多数据"}
            ```
            """.replace("__ACTION__", ACTION_CONFIRM_LOG_AGGREGATION);

    private static final String LOG_AGGREGATION_REVISED_RESPONSE = """
            已根据补充信息更新日志聚合结果，并新增 Web 文件变更记录。请再次确认是否进入沙箱研判。

            ```zenvis:analysis-record
            {
              "recordId": "demo-analysis-log-aggregation-revised",
              "stage": "log_aggregation",
              "status": "completed",
              "title": "日志聚合已补充",
              "content": "在原有访问、进程、网络、登录和规则命中日志基础上，补充关联到 one.jsp 文件变更记录。",
              "startedAt": "2026-07-13 10:01:21",
              "completedAt": "2026-07-13 10:01:35",
              "alarm": {
                "alarmId": "ALM-20260713-0007",
                "name": "WebShell 异常访问",
                "level": "高危",
                "targetHost": "web-01",
                "sourceIp": "10.108.108.23",
                "destIp": "10.106.108.110",
                "path": "/one.jsp"
              },
              "evidenceCount": 7,
              "toolNames": ["retrieval_search", "entity_list"],
              "logs": [
                {"id":"log-001","time":"2026-07-13 09:56:11","type":"http_access","level":"warning","sourceIp":"10.108.108.23","destIp":"10.106.108.110","host":"web-01","method":"POST","path":"/one.jsp","status":200,"message":"同源 IP 对 one.jsp 发起 POST 请求"},
                {"id":"log-002","time":"2026-07-13 09:57:04","type":"http_access","level":"warning","sourceIp":"10.108.108.23","destIp":"10.106.108.110","host":"web-01","method":"POST","path":"/one.jsp","status":200,"message":"请求体包含疑似命令执行参数"},
                {"id":"log-003","time":"2026-07-13 09:58:22","type":"process","level":"high","host":"web-01","process":"cmd.exe","parentProcess":"java.exe","commandLine":"cmd.exe /c whoami","message":"Web 服务进程派生命令行进程"},
                {"id":"log-004","time":"2026-07-13 09:59:10","type":"network","level":"medium","host":"web-01","process":"java.exe","destIp":"172.16.8.45","destPort":4444,"message":"Web 服务进程出现异常外联"},
                {"id":"log-005","time":"2026-07-13 10:00:03","type":"login","level":"info","host":"web-01","account":"svc-web","sourceIp":"10.108.108.23","message":"告警源 IP 关联服务账号访问记录"},
                {"id":"log-006","time":"2026-07-13 10:00:12","type":"rule_hit","level":"high","rule":"webshell_command_execution","message":"命中 WebShell 命令执行检测规则"},
                {"id":"log-007","time":"2026-07-13 09:54:38","type":"file_change","level":"high","host":"web-01","path":"/opt/tomcat/webapps/ROOT/one.jsp","account":"svc-web","message":"one.jsp 在告警前 2 分钟出现新增或覆盖写入"}
              ]
            }
            ```

            ```zenvis:confirm
            {"title":"补充日志聚合已完成，是否进入沙箱研判","content":"已补充 one.jsp 文件变更记录，当前聚合日志共 7 条。确认后进入沙箱研判；如仍需补充，可继续提交数据。","action":"__ACTION__","actions":["approved","revise","rejected"],"reviseLabel":"继续补充数据"}
            ```
            """.replace("__ACTION__", ACTION_CONFIRM_LOG_AGGREGATION);

    private static final String SANDBOX_ANALYSIS_RESPONSE = """
            已进入第二阶段：沙箱研判。沙箱服务已返回 JSON 研判结果，请确认该结果是否满足要求；如不满意，可以补充信息继续研判。

            ```zenvis:analysis-record
            {
              "recordId": "demo-analysis-sandbox",
              "stage": "sandbox_analysis",
              "status": "completed",
              "title": "沙箱研判完成",
              "content": "独立沙箱服务已完成行为链分析，结论指向疑似 WebShell 命令执行与异常外联。",
              "startedAt": "2026-07-13 10:00:19",
              "completedAt": "2026-07-13 10:01:02",
              "sandboxTaskId": "sandbox-demo-20260713-0007",
              "riskLevel": "高危",
              "confidence": 0.91,
              "toolNames": ["analysis_sandbox_analyze"],
              "sandboxResult": {
                "taskId": "sandbox-demo-20260713-0007",
                "verdict": "suspicious_webshell_activity",
                "riskLevel": "high",
                "confidence": 0.91,
                "attackChain": ["异常 JSP 访问", "命令执行参数", "java.exe 派生 cmd.exe", "异常外联"],
                "matchedRules": ["webshell_command_execution", "web_process_spawn_shell", "suspicious_outbound_connection"],
                "evidenceRefs": ["log-001", "log-002", "log-003", "log-004", "log-006"],
                "summary": "访问行为、进程链和外联行为具有一致性，符合 WebShell 利用后的命令执行特征。"
              }
            }
            ```

            ```zenvis:confirm
            {"title":"沙箱研判结果已返回，是否生成分析结论","content":"沙箱判断为疑似 WebShell 命令执行，高危，置信度 91%。确认满意后进入分析结论阶段；如不满意，可补充研判重点继续沙箱研判。","action":"__ACTION__","actions":["approved","revise","rejected"],"reviseLabel":"补充信息继续研判"}
            ```
            """.replace("__ACTION__", ACTION_CONFIRM_SANDBOX_RESULT);

    private static final String SANDBOX_ANALYSIS_REVISED_RESPONSE = """
            已根据补充信息重新执行沙箱研判，重点复核文件变更和外联行为。请再次确认是否生成分析结论。

            ```zenvis:analysis-record
            {
              "recordId": "demo-analysis-sandbox-revised",
              "stage": "sandbox_analysis",
              "status": "completed",
              "title": "沙箱研判已补充",
              "content": "沙箱服务复核了 one.jsp 文件变更、命令执行和异常外联链路，风险判断保持高危。",
              "startedAt": "2026-07-13 10:02:01",
              "completedAt": "2026-07-13 10:02:38",
              "sandboxTaskId": "sandbox-demo-20260713-0007-rerun",
              "riskLevel": "高危",
              "confidence": 0.94,
              "toolNames": ["analysis_sandbox_analyze"],
              "sandboxResult": {
                "taskId": "sandbox-demo-20260713-0007-rerun",
                "verdict": "confirmed_webshell_activity",
                "riskLevel": "high",
                "confidence": 0.94,
                "attackChain": ["JSP 文件新增", "异常 JSP 访问", "命令执行参数", "java.exe 派生 cmd.exe", "异常外联"],
                "matchedRules": ["webshell_file_drop", "webshell_command_execution", "web_process_spawn_shell", "suspicious_outbound_connection"],
                "evidenceRefs": ["log-001", "log-002", "log-003", "log-004", "log-006", "log-007"],
                "summary": "补充文件变更证据后，WebShell 投递、访问、命令执行和外联链路更完整，建议按确认入侵处理。"
              }
            }
            ```

            ```zenvis:confirm
            {"title":"补充沙箱研判已完成，是否生成分析结论","content":"复核后风险判断为高危，置信度提升至 94%。确认满意后生成分析结论；如仍不满意，可继续补充研判重点。","action":"__ACTION__","actions":["approved","revise","rejected"],"reviseLabel":"继续补充研判信息"}
            ```
            """.replace("__ACTION__", ACTION_CONFIRM_SANDBOX_RESULT);

    private static final String ANALYSIS_CONCLUSION_RESPONSE = """
            已进入第三阶段：输出分析结论。以下结论会同步到右侧分析结论时间轴，并生成可编辑研判报告。

            ```zenvis:analysis-record
            {
              "recordId": "demo-analysis-conclusion",
              "stage": "report_output",
              "status": "completed",
              "title": "研判结论已生成",
              "content": "本次告警建议按高危事件处理，优先隔离主机并保留现场。",
              "startedAt": "2026-07-13 10:01:03",
              "completedAt": "2026-07-13 10:01:20",
              "riskLevel": "高危",
              "confidence": 0.91,
              "keyFindings": [
                "同源 IP 对 one.jsp 发起多次 POST 请求，路径与 WebShell 告警一致。",
                "Web 服务进程 java.exe 派生 cmd.exe，存在命令执行证据。",
                "沙箱返回的攻击链与检索日志互相印证。"
              ],
              "recommendations": [
                "临时隔离 web-01，保留进程、网络连接和 Web 目录现场。",
                "阻断源 IP 10.108.108.23 与异常外联地址。",
                "排查 /one.jsp 文件来源和最近变更记录，补充 WebShell 检测规则。"
              ],
              "timeline": [
                {"id":"analysis-target","title":"分析目标","content":"确认 ALM-20260713-0007 WebShell 异常访问告警是否构成真实入侵行为，并评估风险等级。","time":"2026-07-13 10:01:03","type":"primary"},
                {"id":"analysis-process","title":"分析过程","content":"先按源 IP、目标主机、访问路径和时间窗口聚合 6 条相关日志，再将聚合日志提交给独立沙箱服务完成攻击链分析。","time":"2026-07-13 10:01:08","type":"primary"},
                {"id":"analysis-conclusion","title":"分析结论","content":"访问日志、进程链和沙箱规则命中结果一致，判断为疑似 WebShell 命令执行，高危，置信度 91%。","time":"2026-07-13 10:01:15","type":"success"},
                {"id":"disposal-recommendation","title":"处置建议","content":"立即隔离 web-01，阻断源 IP 与异常外联，保留现场后排查 /one.jsp 文件来源，并补充 WebShell 行为检测规则。","time":"2026-07-13 10:01:20","type":"warning"}
              ]
            }
            ```

            ```zenvis:report-document-config
            # WebShell 异常访问告警研判报告

            ## 分析目标

            确认 ALM-20260713-0007 WebShell 异常访问告警是否为真实攻击，并评估风险等级与处置优先级。

            ## 分析过程

            1. 以源 IP、目标主机、访问路径和告警时间为条件聚合 HTTP 访问、进程、网络、登录和规则命中日志。
            2. 将聚合日志提交给独立沙箱分析服务，复核攻击链、命中规则和证据一致性。
            3. 综合日志证据与沙箱 JSON 结果输出研判结论。

            ## 分析结论

            本次告警具备 WebShell 利用后的命令执行特征：异常 JSP 访问、Web 进程派生命令行、异常外联和规则命中结果相互印证。综合判断为高危，置信度 91%。

            ## 处置建议

            - 立即隔离 web-01，保留进程、网络连接、Web 目录和访问日志现场。
            - 阻断源 IP 10.108.108.23 及异常外联地址。
            - 排查 /one.jsp 文件来源、最近变更和上传入口。
            - 将本次攻击链特征补充到 WebShell 行为检测规则中。
            ```

            ```zenvis:analysis-decision
            {"title":"研判完成，请选择后续处理","content":"可以执行处置、忽略告警，或补充研判重点继续分析。","actions":["dispose","ignore","continue"]}
            ```
            """;

    private static final String DEMO_CANCEL_RESPONSE = """
            ```zenvis:notice
            {"title":"研判演示已暂停","content":"已按你的选择暂停当前研判演示流程，未进入下一阶段。","level":"info"}
            ```
            """;

    public Optional<Flux<String>> findResponse(ChatSession chatSession, String chatId, String prompt, User user) {
        if (!StringUtils.hasText(prompt)) {
            return Optional.empty();
        }
        String normalizedPrompt = prompt.trim();
        return findDemoResponse(normalizedPrompt)
                .map(this::streamResponse);
    }

    private Optional<String> findDemoResponse(String prompt) {
        if (isAnalysisDemoPrompt(prompt)) {
            return Optional.of(LOG_AGGREGATION_RESPONSE);
        }
        if (isConfirmLogAggregationPrompt(prompt)) {
            return Optional.of(SANDBOX_ANALYSIS_RESPONSE);
        }
        if (isReviseLogAggregationPrompt(prompt)) {
            return Optional.of(LOG_AGGREGATION_REVISED_RESPONSE);
        }
        if (isConfirmSandboxPrompt(prompt)) {
            return Optional.of(ANALYSIS_CONCLUSION_RESPONSE);
        }
        if (isReviseSandboxPrompt(prompt)) {
            return Optional.of(SANDBOX_ANALYSIS_REVISED_RESPONSE);
        }
        if (isCancelDemoPrompt(prompt)) {
            return Optional.of(DEMO_CANCEL_RESPONSE);
        }
        return Optional.empty();
    }

    private boolean isConfirmLogAggregationPrompt(String prompt) {
        return prompt.contains("我已确认日志聚合结果")
                || prompt.contains(ACTION_CONFIRM_LOG_AGGREGATION);
    }

    private boolean isReviseLogAggregationPrompt(String prompt) {
        return prompt.contains("我需要补充更多日志聚合数据")
                || prompt.contains("已补充日志聚合数据");
    }

    private boolean isConfirmSandboxPrompt(String prompt) {
        return prompt.contains("我已确认沙箱研判结果")
                || prompt.contains(ACTION_CONFIRM_SANDBOX_RESULT);
    }

    private boolean isReviseSandboxPrompt(String prompt) {
        return prompt.contains("我需要补充信息继续沙箱研判")
                || prompt.contains("已补充沙箱研判信息");
    }

    private boolean isCancelDemoPrompt(String prompt) {
        return prompt.contains("我已取消研判演示流程")
                || prompt.contains("取消研判演示");
    }

    public static boolean isAnalysisDemoRelatedPrompt(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return false;
        }
        String normalizedPrompt = prompt.trim();
        return isAnalysisDemoPrompt(normalizedPrompt)
                || normalizedPrompt.contains("我已确认日志聚合结果")
                || normalizedPrompt.contains("我需要补充更多日志聚合数据")
                || normalizedPrompt.contains("已补充日志聚合数据")
                || normalizedPrompt.contains("我已确认沙箱研判结果")
                || normalizedPrompt.contains("我需要补充信息继续沙箱研判")
                || normalizedPrompt.contains("已补充沙箱研判信息")
                || normalizedPrompt.contains("我已取消研判演示流程")
                || normalizedPrompt.contains(ACTION_CONFIRM_LOG_AGGREGATION)
                || normalizedPrompt.contains(ACTION_CONFIRM_SANDBOX_RESULT);
    }

    public static boolean isAnalysisDemoPrompt(String prompt) {
        return StringUtils.hasText(prompt) && ANALYSIS_WEB_SHELL_EXAMPLE_PROMPT.equals(prompt.trim());
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
