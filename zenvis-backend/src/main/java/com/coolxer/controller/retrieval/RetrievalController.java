package com.coolxer.controller.retrieval;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.controller.BaseController;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleCreateDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleDeleteDto;
import com.coolxer.model.retrieval.dto.RetrievalRuleUpdateDto;
import com.coolxer.model.retrieval.rule.RetrievalRule;
import com.coolxer.model.retrieval.vo.DataAttributeResultVo;
import com.coolxer.model.retrieval.vo.DataEntityResultVo;
import com.coolxer.model.retrieval.vo.DataListVo;
import com.coolxer.model.retrieval.vo.IdVo;
import com.coolxer.model.retrieval.vo.RetrievalRuleDetailVo;
import com.coolxer.service.retrieval.RetrievalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 检索
 */
@Tag(name = "检索")
@Slf4j
@RestController
@RequestMapping("/api/v1/retrieval")
public class RetrievalController extends BaseController {

    @Autowired
    RetrievalService retrievalService;

    // 条件检索请求
    // /data/search/criteria

    @PostMapping(value = "/do")
    @Operation(summary = "数据检索", description = "")
    public ResponseWrap<DataListVo> searchByCriteria(@RequestBody RetrievalRequestDto retrievalRequestDTO) {
        validateRetrievalRequest(retrievalRequestDTO);
        DataListVo dataList = retrievalService.retrievalByCriteria(retrievalRequestDTO);
        return ResponseWrap.success(dataList);
    }

    // 创建检索规则
    // /data/search/rule/create

    @PostMapping(value = "/rule/create")
    @Operation(summary = "检索规则创建", description = "检索规则创建")
    public ResponseWrap<IdVo> createSearchRule(@RequestBody RetrievalRuleCreateDto request) {
        Integer id = retrievalService.createRule(request.toRetrievalRequestDto(), currentUserId());
        return ResponseWrap.success(new IdVo(id));
    }

    // 更新检索规则
    // /data/search/rule/update
    @PostMapping(value = "/rule/update")
    @Operation(summary = "检索规则更新", description = "检索规则更新")
    public ResponseWrap<IdVo> updateSearchRule(@RequestBody RetrievalRuleUpdateDto request) {
        Integer id = retrievalService.updateRule(request.toRetrievalRequestDto(), currentUserId());
        return ResponseWrap.success(new IdVo(id));
    }

    // 更新检索规则
    // /data/search/rule/update
    @PostMapping(value = "/rule/delete")
    @Operation(summary = "检索规则删除", description = "检索规则删除")
    public ResponseWrap<Void> deleteSearchRule(@RequestBody RetrievalRuleDeleteDto request) {
        Boolean ret = retrievalService.deleteRule(request == null ? null : request.getId(), currentUserId());
        return ret ? ResponseWrap.success() : ResponseWrap.fail();
    }

    // 检索规则列表
    @GetMapping(value = "/rule/list")
    @Operation(summary = "检索规则列表", description = "检索规则列表")
    public ResponseWrap<DataListVo> listSearchRule() {
        DataListVo dataList = retrievalService.listRule(currentUserId());
        return ResponseWrap.success(dataList);
    }

    // 获取指定检索规则
    @GetMapping(value = "/rule/get")
    @Operation(summary = "获取指定检索规则", description = "获取指定检索规则")
    public ResponseWrap<RetrievalRule> getSearchRule(@RequestParam(value = "id") Integer id) {
        return ResponseWrap.success(retrievalService.getRule(id, currentUserId()));
    }

    @GetMapping(value = "/rule/detail")
    @Operation(summary = "获取检索规则编辑详情", description = "返回逻辑配置、当前元数据及失效项")
    public ResponseWrap<RetrievalRuleDetailVo> getSearchRuleDetail(@RequestParam(value = "id") Integer id) {
        return ResponseWrap.success(retrievalService.getRuleDetail(id, currentUserId()));
    }

    // 根据检索规则id请求检索
    public void searchByRuleId(Integer id) {
        retrievalService.retrievalByRuleId(id, currentUserId());
    }

    // 数据实体列表
    @GetMapping(value = "/entity/list")
    @Operation(summary = "获取实体列表", description = "获取实体列表")
    public ResponseWrap<DataEntityResultVo> listEntity(
            @RequestParam(value = "rule_id", required = false) Integer ruleId) {
        DataEntityResultVo dataEntityResultVo = retrievalService.listEntity(ruleId, currentUserId());
        return ResponseWrap.success(dataEntityResultVo);
    }

    // 数据属性列表
    @GetMapping(value = "/attribute/list")
    @Operation(summary = "获取属性列表", description = "获取属性列表")
    public ResponseWrap<DataAttributeResultVo> listAttribute(
            @RequestParam(value = "entity", required = false) String entity,
            @RequestParam(value = "rule_id", required = false) Integer ruleId) {
        DataAttributeResultVo dataAttributeResultVo = retrievalService.listAttribute(entity, ruleId, currentUserId());
        return ResponseWrap.success(dataAttributeResultVo);
    }

    // 获取指定属性值列表
    @GetMapping(value = "/candidate/list")
    @Operation(summary = "获取指定字段备选信息", description = "获取指定字段备选信息")
    public ResponseWrap<DataListVo> listCandidateValue(
            @RequestParam(value = "attributeId", required = false) Integer attributeId,
            @RequestParam(value = "entity", required = false) String entity,
            @RequestParam(value = "attribute", required = false) String attribute,
            @RequestParam(value = "text", required = false) String text) {
        DataListVo dataList;
        if (entity != null && attribute != null) {
            dataList = retrievalService.listCandidate(entity, attribute, text);
        } else {
            dataList = retrievalService.listCandidate(attributeId, text);
        }
        return ResponseWrap.success(dataList);
    }

    @GetMapping(value = "/display/entity/list")
    @Operation(summary = "获取展示实体列表", description = "获取展示实体列表")
    public ResponseWrap<DataEntityResultVo> listDisplayEntity(
            @RequestParam(value = "rule_id", required = false) Integer ruleId) {
        DataEntityResultVo dataEntityResultVo = retrievalService.listEntity(ruleId, currentUserId());
        return ResponseWrap.success(dataEntityResultVo);
    }

    @GetMapping(value = "/display/attribute/list")
    @Operation(summary = "获取展示属性列表", description = "获取展示属性列表")
    public ResponseWrap<DataAttributeResultVo> listDisplayAttribute(
            @RequestParam(value = "entity", required = false) String entity,
            @RequestParam(value = "rule_id", required = false) Integer ruleId) {
        DataAttributeResultVo dataAttributeResultVo = retrievalService.listAttributeForDisplay(entity, ruleId, currentUserId());
        return ResponseWrap.success(dataAttributeResultVo);
    }

    private void validateRetrievalRequest(RetrievalRequestDto retrievalRequestDTO) {
        if (retrievalRequestDTO == null) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索请求不能为空");
        }
        if (retrievalRequestDTO.getDisplayList() == null || retrievalRequestDTO.getDisplayList().isEmpty()
                || retrievalRequestDTO.getDisplayList().get(0) == null
                || retrievalRequestDTO.getDisplayList().get(0).getAttributeList() == null) {
            throw new ApiException(ResultCodeEnum.DISPLAY_LIMIT_ERROR);
        }
    }

    private Integer currentUserId() {
        com.coolxer.dao.mysql.entity.User user = getSessionUser();
        if (user == null) {
            throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), "当前用户未登录");
        }
        return user.getId();
    }

}
