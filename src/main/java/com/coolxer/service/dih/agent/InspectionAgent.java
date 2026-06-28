package com.coolxer.service.dih.agent;

import com.coolxer.service.dih.agent.nl2sql.connector.MdTableGenerator;
import com.coolxer.service.dih.agent.nl2sql.connector.bo.ResultSetBO;
import com.coolxer.service.dih.agent.nl2sql.constant.Constant;
import com.coolxer.service.dih.agent.dto.schema.SchemaDTO;
import com.coolxer.service.dih.agent.nl2sql.service.RedisNl2sqlService;
import com.coolxer.service.dih.agent.nl2sql.service.base.BaseSchemaService;
import com.coolxer.service.dih.agent.skill.SkillService;
import com.coolxer.service.dih.agent.nl2sql.util.SqlSafeValidator;
import com.coolxer.service.dih.agent.nl2sql.util.SqlValidationResult;
import com.coolxer.commons.enums.MessageType;
import com.coolxer.model.dih.ChatResponse;
import com.coolxer.service.dih.agent.converter.EChartsConverter;
import com.coolxer.service.dih.agent.dto.EChartsData;
import com.coolxer.service.dih.agent.dto.EChartsIntention;
import com.coolxer.service.dih.agent.nl2sql.service.LlmService;
import com.coolxer.utils.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class InspectionAgent {

    /** SQL执行失败最大重试次数 */
    private static final int MAX_SQL_RETRY = 2;

    /** CHART 类型响应存储到记忆中的摘要文本模板 */
    private static final String CHART_MEMORY_TEMPLATE = "[图表数据已展示，图表类型：%s]";

    /** 记忆中嵌入 SQL 的标记格式 */
    private static final String SQL_MEMORY_TAG = "[SQL: %s]";

    /** 从记忆中提取 SQL 的正则（DOTALL 使 . 匹配换行符） */
    private static final Pattern SQL_MEMORY_PATTERN = Pattern.compile("\\[SQL:\\s*(.+?)\\]", Pattern.DOTALL);

    @Data
    @AllArgsConstructor
    static class Nl2SqlContext {
        private String sql;
        private String query;
        private List<String> evidences;
        private SchemaDTO schemaDTO;
    }

    private final RedisNl2sqlService baseNl2SqlService;
    private final BaseSchemaService baseSchemaService;
    private final EChartsConverter eChartsConverter;
    private final LlmService llmService;
    private final PromptTemplate systemPromptTemplate;
    private final ChatMemory chatMemory;
    private final SkillService skillService;

    public InspectionAgent(@Qualifier("redisNl2sqlService") RedisNl2sqlService baseNl2SqlService,
                           @Qualifier("redisSchemaService") BaseSchemaService baseSchemaService,
                           EChartsConverter eChartsConverter,
                           @Qualifier("llmService") LlmService llmService,
                           @Qualifier("agentInspectSystemPromptTemplate") PromptTemplate systemPromptTemplate,
                           @Qualifier("inspectionAgentChatMemory") ChatMemory chatMemory,
                           SkillService skillService) {
        this.baseNl2SqlService = baseNl2SqlService;
        this.baseSchemaService = baseSchemaService;
        this.eChartsConverter = eChartsConverter;
        this.llmService = llmService;
        this.systemPromptTemplate = systemPromptTemplate;
        this.chatMemory = chatMemory;
        this.skillService = skillService;
    }

    /**
     * 根据Nl2Sql-Graph的定义，抽取其自然语言转化为sql的功能代码
     *
     * @param query 自然语言
     * @return 包含sql和生成上下文的结果
     */
    public Nl2SqlContext nl2sql(String query) throws Exception {
        return nl2sql(query, null);
    }

    /**
     * 带对话上下文的 nl2sql
     *
     * @param query 自然语言
     * @param conversationHistory 对话历史（可为null，表示无记忆）
     * @return 包含sql和生成上下文的结果
     */
    public Nl2SqlContext nl2sql(String query, List<Message> conversationHistory) throws Exception {

        // 1. query rewrite（注入对话历史以解析指代和追问）
        query = baseNl2SqlService.rewrite(query, conversationHistory);
        if (Constant.INTENT_UNCLEAR.equals(query) || Constant.SMALL_TALK_REJECT.equals(query)) {
            throw new IllegalArgumentException("输入的自然语言属于【".concat(query).concat("】，无法转为SQL语言"));
        }
        log.info("问题重写结果：{}", query);

        // 2. keyword extract
        List<String> expandedQuestions = baseNl2SqlService.expandQuestion(query);
        log.info("问题扩展结果: {}", expandedQuestions);
        List<String> evidences = baseNl2SqlService.extractEvidences(query);
        List<String> keywords = baseNl2SqlService.extractKeywords(query, evidences);
        log.info("增强提取结果 - 证据: {}, 关键词: {}", evidences, keywords);

        // 3. schema recall
        List<Document> tableDocuments = baseSchemaService.getTableDocuments(query);
        List<List<Document>> columnDocumentsByKeywords = baseSchemaService.getColumnDocumentsByKeywords(keywords);
        log.info("Schema recall results - table documents count: {}, keyword-related column document groups: {}",
                tableDocuments.size(), columnDocumentsByKeywords.size());

        // 4. table relation
        SchemaDTO schemaDTO = new SchemaDTO();
        baseSchemaService.extractDatabaseName(schemaDTO);
        baseSchemaService.buildSchemaFromDocuments(columnDocumentsByKeywords, tableDocuments, schemaDTO);
        log.info("Executing regular schema selection");
        schemaDTO = baseNl2SqlService.fineSelect(schemaDTO, query, evidences);
        log.info("Schema result: {}", schemaDTO);

        // 5. nl2sql（如有历史SQL，作为参考传给生成步骤以保持一致性）
        String previousSql = extractPreviousSql(conversationHistory);
        String sqlQuery = query;
        if (previousSql != null) {
            sqlQuery = query + "\n\n【参考SQL】以下是针对类似历史查询生成的SQL，请基于此SQL进行修改，仅调整与新查询不同的部分：\n" + previousSql;
            log.info("注入历史参考SQL用于SQL生成");
        }
        String sql = baseNl2SqlService.generateSql(evidences, sqlQuery, schemaDTO);
        return new Nl2SqlContext(sql, query, evidences, schemaDTO);
    }

    public ChatResponse chat(String query) {
        return chat(query, null, null);
    }

    public ChatResponse chat(String query, String model) {
        return chat(query, model, null);
    }

    public ChatResponse chat(String query, String model, String chatId) {
        if (model != null) {
            llmService.setModel(model);
        }
        try {
            // 获取对话历史，在整个 chat 流程中复用
            List<Message> conversationHistory = getConversationHistory(chatId);

            Nl2SqlContext ctx;
            try {
                ctx = nl2sql(query, conversationHistory);
            } catch (IllegalArgumentException e) {
                log.info("Intent classified as non-SQL: {}", e.getMessage());
                return handleNonSqlIntent(query, e, chatId, conversationHistory);
            } catch (Exception e) {
                log.error("nl2sql exception：", e);
                return ChatResponse.builder()
                        .content("生成失败，请重试")
                        .type(MessageType.TEXT)
                        .build();
            }

        String sql = ctx.getSql();
        SqlValidationResult validationResult = SqlSafeValidator.validate(sql);
        if (!validationResult.isValid()) {
            log.warn("SQL 安全校验未通过，拒绝执行。原因: {}，SQL: {}",
                    validationResult.getRejectionReason(), sql);
            return ChatResponse.builder()
                    .content("生成的SQL语句未通过安全校验: " + validationResult.getRejectionReason())
                    .type(MessageType.TEXT)
                    .build();
        }

        // 执行SQL，失败时重试修复
        ResultSetBO sqlRes = null;
        Exception lastError = null;
        for (int attempt = 0; attempt <= MAX_SQL_RETRY; attempt++) {
            try {
                sqlRes = baseNl2SqlService.executeSqlOri(sql);
                lastError = null;
                break;
            } catch (Exception e) {
                lastError = e;
                if (attempt < MAX_SQL_RETRY) {
                    String errorMsg = e.getMessage();
                    log.warn("SQL execution failed (attempt {}/{}): {}", attempt + 1, MAX_SQL_RETRY + 1, errorMsg);
                    try {
                        sql = baseNl2SqlService.generateSql(ctx.getEvidences(), ctx.getQuery(),
                                ctx.getSchemaDTO(), sql, errorMsg);
                        log.info("SQL retry #{} generated: {}", attempt + 1, sql);
                    } catch (Exception retryEx) {
                        log.error("SQL retry generation failed", retryEx);
                        break;
                    }
                }
            }
        }

        if (lastError != null) {
            log.error("SQL execution failed after {} retries", MAX_SQL_RETRY + 1, lastError);
            return ChatResponse.builder()
                    .content("执行失败，请重试")
                    .type(MessageType.TEXT)
                    .build();
        }
        log.info("executed result: {}", sqlRes);

        EChartsIntention intention = baseNl2SqlService.needRenderECharts(query, conversationHistory);
        if (!intention.getNeedECharts()) {
            String result = MdTableGenerator.generateTable(sqlRes);
            log.info("SQL execution completed successfully, result rows: {}",
                    sqlRes.getData() != null ? sqlRes.getData().size() : 0);
            ChatResponse response = ChatResponse.builder()
                    .content(result)
                    .type(MessageType.TEXT)
                    .build();
            saveToMemory(chatId, query, response, null, sql);
            return response;
        }

        // 转化为对应的 ECharts 数据结构
        String chartType = intention.getChartType();
        if (chartType == null || chartType.isEmpty()) {
            chartType = "bar"; // 默认柱状图
        }

        EChartsData echartsData = eChartsConverter.convert(sqlRes, chartType);
        if (echartsData == null) {
            log.warn("Failed to convert result to ECharts data, falling back to table");
            String tableResult = MdTableGenerator.generateTable(sqlRes);
            ChatResponse response = ChatResponse.builder()
                    .content(tableResult)
                    .type(MessageType.TEXT)
                    .build();
            saveToMemory(chatId, query, response, null, sql);
            return response;
        }

        log.info("Successfully converted to {} chart, data rows: {}",
                chartType, sqlRes.getData() != null ? sqlRes.getData().size() : 0);

        // 返回 JSON 格式的 ECharts 数据，前端可直接解析渲染
        String chartJson = JacksonUtil.toJson(echartsData);
        ChatResponse response = ChatResponse.builder()
                .content(chartJson)
                .type(MessageType.CHART)
                .build();
        saveToMemory(chatId, query, response, chartType, sql);
        return response;
        } finally {
            llmService.clearModel();
        }
    }


    /**
     * 处理非SQL查询意图（闲聊或意图不明确）
     */
    private ChatResponse handleNonSqlIntent(String originalQuery, IllegalArgumentException e, String chatId,
                                            List<Message> conversationHistory) {
        String message = e.getMessage();
        if (message != null && message.contains(Constant.SMALL_TALK_REJECT)) {
            return handleSmallTalk(originalQuery, chatId, conversationHistory);
        } else if (message != null && message.contains(Constant.INTENT_UNCLEAR)) {
            ChatResponse response = handleIntentUnclear();
            saveToMemory(chatId, originalQuery, response);
            return response;
        }
        return ChatResponse.builder()
                .content("生成失败，请重试")
                .type(MessageType.TEXT)
                .build();
    }

    /**
     * 处理闲聊问题：使用对话系统提示直接回答，带对话上下文
     */
    private ChatResponse handleSmallTalk(String query, String chatId, List<Message> conversationHistory) {
        try {
            String systemPrompt = systemPromptTemplate.getTemplate();
            String skillPrompt = skillService.buildEnabledSkillPrompt("agent_inspect");
            if (StringUtils.hasText(skillPrompt)) {
                systemPrompt = systemPrompt + "\n\n【已加载 Skill】\n" + skillPrompt;
            }

            // 构建带对话上下文的用户 prompt
            String userPrompt = query;
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Message msg : conversationHistory) {
                    org.springframework.ai.chat.messages.MessageType type = msg.getMessageType();
                    if (type == org.springframework.ai.chat.messages.MessageType.USER) {
                        sb.append(">用户：").append(msg.getText()).append("\n");
                    } else if (type == org.springframework.ai.chat.messages.MessageType.ASSISTANT) {
                        sb.append(">助理：").append(msg.getText()).append("\n");
                    }
                }
                sb.append("<最新>用户：").append(query);
                userPrompt = sb.toString();
            }

            String response = llmService.callWithSystemPrompt(systemPrompt, userPrompt);
            ChatResponse chatResponse = ChatResponse.builder()
                    .content(response)
                    .type(MessageType.TEXT)
                    .build();
            saveToMemory(chatId, query, chatResponse);
            return chatResponse;
        } catch (Exception e) {
            log.error("Failed to handle small talk with LLM", e);
            return ChatResponse.builder()
                    .content("抱歉，我暂时无法回答这个问题。我是巡检智能体，主要帮助您进行日志数据的查询与分析，请尝试提出与数据相关的问题。")
                    .type(MessageType.TEXT)
                    .build();
        }
    }

    /**
     * 处理意图不明确的情况：返回引导性提示
     */
    private ChatResponse handleIntentUnclear() {
        return ChatResponse.builder()
                .content("抱歉，我还不确定您想查询什么。能否请您补充更多细节？例如：\n" +
                        "- 您想查询哪个时间范围的数据？\n" +
                        "- 您关注的是哪些字段或指标？\n" +
                        "- 您需要统计、对比还是趋势分析？")
                .type(MessageType.TEXT)
                .build();
    }

    /**
     * 获取对话历史
     */
    private List<Message> getConversationHistory(String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            return List.of();
        }
        try {
            return chatMemory.get(chatId);
        } catch (Exception e) {
            log.warn("Failed to get conversation history for chatId: {}", chatId, e);
            return List.of();
        }
    }

    /**
     * 将用户查询和AI响应保存到对话记忆
     */
    private void saveToMemory(String chatId, String userQuery, ChatResponse response) {
        saveToMemory(chatId, userQuery, response, null, null);
    }

    /**
     * 将用户查询和AI响应保存到对话记忆
     * @param chartType 图表类型（仅 CHART 响应时使用）
     * @param sql 生成的SQL（存储到记忆中以支持后续追问复用参考）
     */
    private void saveToMemory(String chatId, String userQuery, ChatResponse response, String chartType, String sql) {
        if (chatId == null || chatId.isEmpty()) {
            return;
        }
        try {
            String responseContent = response.getContent();
            if (response.getType() == MessageType.CHART) {
                responseContent = String.format(CHART_MEMORY_TEMPLATE, chartType);
            }
            // 在响应内容后追加 SQL 引用，供后续 SQL 生成步骤提取
            if (sql != null && !sql.isEmpty()) {
                responseContent += " " + String.format(SQL_MEMORY_TAG, sql);
            }
            chatMemory.add(chatId, List.of(
                    new UserMessage(userQuery),
                    new AssistantMessage(responseContent)
            ));
        } catch (Exception e) {
            log.warn("Failed to save conversation memory for chatId: {}", chatId, e);
        }
    }

    /**
     * 从对话历史中提取最近一次生成的 SQL
     */
    private String extractPreviousSql(List<Message> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return null;
        }
        // 从后往前查找最近的 SQL 标记
        for (int i = conversationHistory.size() - 1; i >= 0; i--) {
            Message msg = conversationHistory.get(i);
            if (msg.getMessageType() == org.springframework.ai.chat.messages.MessageType.ASSISTANT) {
                Matcher matcher = SQL_MEMORY_PATTERN.matcher(msg.getText());
                if (matcher.find()) {
                    return matcher.group(1).trim();
                }
            }
        }
        return null;
    }
}
