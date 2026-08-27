package com.coolxer.controller.retrieval;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.retrieval.analytics.AnalyticsResponse;
import com.coolxer.model.retrieval.analytics.AggregateQueryRequest;
import com.coolxer.model.retrieval.analytics.DistributionQueryRequest;
import com.coolxer.model.retrieval.analytics.HistogramQueryRequest;
import com.coolxer.model.retrieval.analytics.OverviewQueryRequest;
import com.coolxer.model.retrieval.analytics.RelationQueryRequest;
import com.coolxer.model.retrieval.analytics.RelationTimelineQueryRequest;
import com.coolxer.model.retrieval.analytics.ScatterQueryRequest;
import com.coolxer.model.retrieval.analytics.SummaryQueryRequest;
import com.coolxer.model.retrieval.analytics.TrendQueryRequest;
import com.coolxer.model.retrieval.analytics.ValueStatisticsQueryRequest;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleCreateDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleDeleteDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleUpdateDto;
import com.coolxer.model.retrieval.vo.DataAttributeResultVo;
import com.coolxer.model.retrieval.vo.DataEntityResultVo;
import com.coolxer.model.retrieval.vo.DataListVo;
import com.coolxer.service.retrieval.EntityCoreService;
import com.coolxer.service.retrieval.EntityAnalyticsService;
import com.coolxer.service.retrieval.RetrievalService;
import com.coolxer.service.dih.mcp.McpToolApproval;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpInvocationContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.coolxer.commons.enums.McpApprovalPolicy.ALLOW;
import static com.coolxer.commons.enums.McpApprovalPolicy.ASK;
import static com.coolxer.commons.enums.McpToolRiskLevel.HIGH;
import static com.coolxer.commons.enums.McpToolRiskLevel.LOW;

/**
 * MCP工具服务 - 暴露检索相关接口为MCP工具
 */
@Slf4j
@Service
public class RetrievalMcpTool {

    private static final int DEFAULT_MCP_PAGE_SIZE = 20;

    private static final int MAX_MCP_PAGE_SIZE = 50;

    @Autowired
    private EntityCoreService entityCoreService;

    @Autowired
    private EntityAnalyticsService entityAnalyticsService;

    @Autowired
    private RetrievalService retrievalService;

    /**
     * 数据检索
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "retrieval_search", description = "根据条件检索数据，返回符合条件的列表数据；默认20条，单次最多50条")
    public DataListVo searchByCriteria(@ToolParam(description = "检索请求参数，包含实体、查询条件、显示字段等") RetrievalRequestDto request) {
        if (request != null) {
            request.setSize(boundedPageSize(request.getSize()));
        }
        return retrievalService.retrievalByCriteria(request);
    }

    /**
     * 创建检索规则
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "retrieval_create_rule", description = "创建一个新的检索规则")
    public Boolean createSearchRule(@ToolParam(description = "检索规则请求参数") RetrievalRuleCreateDto request) {
        return retrievalService.createRule(request == null ? null : request.toRetrievalRequestDto(), currentUserId()) != null;
    }

    /**
     * 更新检索规则
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "retrieval_update_rule", description = "更新已有的检索规则")
    public Boolean updateSearchRule(@ToolParam(description = "检索规则请求参数") RetrievalRuleUpdateDto request) {
        return retrievalService.updateRule(request == null ? null : request.toRetrievalRequestDto(), currentUserId()) != null;
    }

    /**
     * 删除检索规则
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "retrieval_delete_rule", description = "删除指定的检索规则")
    public Boolean deleteSearchRule(@ToolParam(description = "检索规则请求参数，包含规则ID") RetrievalRuleDeleteDto request) {
        return retrievalService.deleteRule(request == null ? null : request.getId(), currentUserId());
    }

    /**
     * 获取检索规则列表
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "retrieval_list_rule", description = "获取所有检索规则列表")
    public DataListVo listSearchRule() {
        return retrievalService.listRule(currentUserId());
    }

    /**
     * 获取实体列表
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "retrieval_list_entity",
            description = "获取数据实体Meta列表；后续查询必须使用返回的entityList[].name逻辑名称，并可展示对应label")
    public DataEntityResultVo listEntity(@ToolParam(description = "规则ID，可选", required = false) Integer ruleId) {
        return retrievalService.listEntity(ruleId, currentUserId());
    }

    /**
     * 获取属性列表
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "retrieval_list_attribute",
            description = "按准确实体逻辑名称获取字段Meta；后续查询字段必须使用返回的attributeList[].name，并可展示对应label")
    public DataAttributeResultVo listAttribute(@ToolParam(description = "实体名称，可选", required = false) String entity,
                                               @ToolParam(description = "规则ID，可选", required = false) Integer ruleId) {
        return retrievalService.listAttribute(entity, ruleId, currentUserId());
    }

    /**
     * 获取指定字段备选信息
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "retrieval_list_candidate", description = "获取指定属性的候选值列表")
    public DataListVo listCandidateValue(@ToolParam(description = "属性ID") Integer attributeId,
                                          @ToolParam(description = "搜索文本，可选", required = false) String text) {
        return retrievalService.listCandidate(attributeId, text);
    }

    /**
     * 获取展示实体列表
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "retrieval_list_display_entity",
            description = "获取展示用实体Meta列表；必须从entityList[].name选择准确逻辑实体名称，并同时保留label供用户确认")
    public DataEntityResultVo listDisplayEntity(@ToolParam(description = "规则ID，可选", required = false) Integer ruleId) {
        return retrievalService.listEntity(ruleId, currentUserId());
    }

    /**
     * 获取展示属性列表
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "retrieval_list_display_attribute",
            description = "按准确实体逻辑名称获取展示字段Meta；必须从attributeList[].name选择字段，并同时保留label供用户确认")
    public DataAttributeResultVo listDisplayAttribute(@ToolParam(description = "实体名称，可选", required = false) String entity,
                                                      @ToolParam(description = "规则ID，可选", required = false) Integer ruleId) {
        return retrievalService.listAttributeForDisplay(entity, ruleId, currentUserId());
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_overview", description = "统计多个实体的累计量、当前周期量和对比周期")
    public AnalyticsResponse entityOverview(
            @ToolParam(description = "实体概览查询请求") OverviewQueryRequest request) {
        return entityAnalyticsService.overview(request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_summary", description = "汇总单个实体的多个统计指标")
    public AnalyticsResponse entitySummary(
            @ToolParam(description = "实体指标汇总请求") SummaryQueryRequest request) {
        return entityAnalyticsService.summary(request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_trend", description = "按时间粒度统计一个或多个实体指标趋势")
    public AnalyticsResponse entityTrend(
            @ToolParam(description = "实体趋势查询请求") TrendQueryRequest request) {
        return entityAnalyticsService.trend(request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_distribution", description = "按任意标量字段分组统计TopN，最大100")
    public AnalyticsResponse entityDistribution(
            @ToolParam(description = "实体字段分布查询请求") DistributionQueryRequest request) {
        return entityAnalyticsService.distribution(request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_aggregate",
            description = "使用Meta逻辑字段执行最多两个维度的多指标聚合、分组趋势或热力透视")
    public AnalyticsResponse entityAggregate(
            @ToolParam(description = "单实体多维聚合请求，不允许SQL或物理字段")
            AggregateQueryRequest request) {
        return entityAnalyticsService.aggregate(request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_histogram",
            description = "统计一个Meta数值字段的区间分布并返回ECharts直方图")
    public AnalyticsResponse entityHistogram(
            @ToolParam(description = "单实体数值直方图请求") HistogramQueryRequest request) {
        return entityAnalyticsService.histogram(request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_scatter",
            description = "查询两个Meta数值字段的有限、稳定排序散点或气泡数据")
    public AnalyticsResponse entityScatter(
            @ToolParam(description = "单实体散点图或气泡图请求") ScatterQueryRequest request) {
        return entityAnalyticsService.scatter(request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_value_statistics", description = "统计指定值在任意实体字段中的命中数量")
    public AnalyticsResponse entityValueStatistics(
            @ToolParam(description = "指定值统计请求") ValueStatisticsQueryRequest request) {
        return entityAnalyticsService.valueStatistics(request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_relations", description = "按任意源字段和目标字段聚合指定值的关系")
    public AnalyticsResponse entityRelations(
            @ToolParam(description = "实体关系查询请求") RelationQueryRequest request) {
        return entityAnalyticsService.relations(request);
    }

    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_relation_timeline", description = "按任意关系和分类字段统计时间轴")
    public AnalyticsResponse entityRelationTimeline(
            @ToolParam(description = "实体关系时间轴请求") RelationTimelineQueryRequest request) {
        return entityAnalyticsService.relationTimeline(request);
    }

    /**
     * 实体添加
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "entity_add", description = "向指定实体添加一条记录")
    public Boolean entityAdd(@ToolParam(description = "实体名称") String entity,
                             @ToolParam(description = "记录数据，Map形式") Map<String, Object> data) {
        return entityCoreService.add(entity, data);
    }

    /**
     * 实体删除
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "entity_delete", description = "删除指定实体的记录")
    public Boolean entityDelete(@ToolParam(description = "实体名称") String entity,
                                @ToolParam(description = "平台记录ID（zenvis_id UUID）") String id) {
        entityCoreService.delete(entity, id);
        return true;
    }

    /**
     * 批量删除实体记录
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "entity_bulk_delete", description = "批量删除指定实体的记录")
    public Boolean entityBulkDelete(@ToolParam(description = "实体名称") String entity,
                                    @ToolParam(description = "平台记录ID列表（zenvis_id UUID）") List<String> ids) {
        entityCoreService.deleteALL(entity, ids);
        return true;
    }

    /**
     * 更新实体记录
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "entity_update", description = "更新指定实体的记录")
    public Boolean entityUpdate(@ToolParam(description = "实体名称") String entity,
                                @ToolParam(description = "平台记录ID（zenvis_id UUID）") String id,
                                @ToolParam(description = "更新数据，Map形式") Map<String, Object> data) {
        return entityCoreService.update(entity, id, data);
    }

    /**
     * 批量更新实体记录
     */
    @McpToolApproval(value = ASK, risk = HIGH)
    @Tool(name = "entity_bulk_update", description = "批量更新指定实体的记录")
    public Boolean entityBulkUpdate(@ToolParam(description = "实体名称") String entity,
                                    @ToolParam(description = "平台记录ID列表（zenvis_id UUID）") String[] ids,
                                    @ToolParam(description = "更新数据，Map形式") Map<String, Object> data) {
        return entityCoreService.updateALL(entity, ids == null ? null : java.util.Arrays.asList(ids), data);
    }

    /**
     * 获取实体列表（分页）
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_list", description = "获取指定实体的分页列表数据；默认20条，单次最多50条")
    public PageRowsVo<Map<String, Object>> entityList(@ToolParam(description = "实体名称") String entity,
                                                       @ToolParam(description = "查询参数，Map形式") Map<String, Object> params) {
        Map<String, Object> boundedParams =
                params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        Object requestedSize = boundedParams.containsKey("perPage")
                ? boundedParams.get("perPage")
                : boundedParams.get("per_page");
        boundedParams.put("perPage", boundedPageSize(requestedSize));
        return entityCoreService.getPageList(entity, boundedParams);
    }

    /**
     * 获取实体详情
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_view", description = "获取指定实体的单条记录详情")
    public Map<String, Object> entityView(@ToolParam(description = "实体名称") String entity,
                                           @ToolParam(description = "平台记录ID（zenvis_id UUID）") String id) {
        return entityCoreService.getOne(entity, id);
    }

    private Integer currentUserId() {
        McpInvocationContext context = McpInvocationContextHolder.current();
        return context == null ? null : context.requesterUserId();
    }

    private int boundedPageSize(Integer requestedSize) {
        return requestedSize == null
                ? DEFAULT_MCP_PAGE_SIZE
                : Math.max(1, Math.min(requestedSize, MAX_MCP_PAGE_SIZE));
    }

    private int boundedPageSize(Object requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_MCP_PAGE_SIZE;
        }
        try {
            return boundedPageSize(Integer.parseInt(requestedSize.toString()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_MCP_PAGE_SIZE;
        }
    }
}
