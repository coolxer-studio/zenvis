package com.coolxer.controller.retrieval;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleCreateDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleDeleteDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleUpdateDto;
import com.coolxer.model.retrieval.vo.DataAttributeResultVo;
import com.coolxer.model.retrieval.vo.DataEntityResultVo;
import com.coolxer.model.retrieval.vo.DataListVo;
import com.coolxer.service.retrieval.EntityCoreService;
import com.coolxer.service.retrieval.RetrievalService;
import com.coolxer.service.dih.mcp.McpToolApproval;
import com.coolxer.service.dih.mcp.McpInvocationContext;
import com.coolxer.service.dih.mcp.McpInvocationContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    @Autowired
    private EntityCoreService entityCoreService;

    @Autowired
    private RetrievalService retrievalService;

    /**
     * 数据检索
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "retrieval_search", description = "根据条件检索数据，返回符合条件的列表数据")
    public DataListVo searchByCriteria(@ToolParam(description = "检索请求参数，包含实体、查询条件、显示字段等") RetrievalRequestDto request) {
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
    @Tool(name = "retrieval_list_entity", description = "获取数据实体列表，可按规则ID过滤")
    public DataEntityResultVo listEntity(@ToolParam(description = "规则ID，可选", required = false) Integer ruleId) {
        return retrievalService.listEntity(ruleId, currentUserId());
    }

    /**
     * 获取属性列表
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "retrieval_list_attribute", description = "获取数据属性列表，可按实体或规则ID过滤")
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
    @Tool(name = "retrieval_list_display_entity", description = "获取展示用的实体列表，可按规则ID过滤")
    public DataEntityResultVo listDisplayEntity(@ToolParam(description = "规则ID，可选", required = false) Integer ruleId) {
        return retrievalService.listEntity(ruleId, currentUserId());
    }

    /**
     * 获取展示属性列表
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "retrieval_list_display_attribute", description = "获取展示用的属性列表，可按实体或规则ID过滤")
    public DataAttributeResultVo listDisplayAttribute(@ToolParam(description = "实体名称，可选", required = false) String entity,
                                                      @ToolParam(description = "规则ID，可选", required = false) Integer ruleId) {
        return retrievalService.listAttributeForDisplay(entity, ruleId, currentUserId());
    }

    /**
     * 实体计数 - 统计多个实体的数量
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_count", description = "统计多个实体的数量")
    public Map<String, Object> entityCount(@ToolParam(description = "实体名称列表") List<String> entities) {
        return entityCoreService.count(entities);
    }

    /**
     * 实体趋势 - 获取多个实体的趋势数据
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_trend", description = "获取多个实体的趋势数据")
    public Map<String, Object> entityTrend(@ToolParam(description = "实体名称列表") List<String> entities) {
        return entityCoreService.trend(entities);
    }

    /**
     * 实体统计 - 对多个实体的指定字段进行统计
     */
    @McpToolApproval(value = ALLOW, risk = LOW)
    @Tool(name = "entity_statistics", description = "对多个实体的指定字段进行统计")
    public Map<String, Object> entityStatistics(@ToolParam(description = "实体名称列表") List<String> entities,
                                                 @ToolParam(description = "统计字段名") String field) {
        return entityCoreService.statistics(entities, field);
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
    @Tool(name = "entity_list", description = "获取指定实体的分页列表数据")
    public PageRowsVo<Map<String, Object>> entityList(@ToolParam(description = "实体名称") String entity,
                                                       @ToolParam(description = "查询参数，Map形式") Map<String, Object> params) {
        return entityCoreService.getPageList(entity, params);
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
}
