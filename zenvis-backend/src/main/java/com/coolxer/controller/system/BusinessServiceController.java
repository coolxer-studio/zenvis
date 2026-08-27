package com.coolxer.controller.system;

import com.coolxer.model.base.vo.PageRowsVo;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.system.dto.BusinessServiceEventSearchDto;
import com.coolxer.model.system.dto.BusinessServiceInstanceSearchDto;
import com.coolxer.model.system.vo.BusinessServiceEventVo;
import com.coolxer.model.system.vo.BusinessServiceInstanceVo;
import com.coolxer.model.system.vo.BusinessServiceSummaryVo;
import com.coolxer.service.system.BusinessServiceRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "业务应用服务")
@RestController
@RequestMapping("/api/v1/system/business-services")
public class BusinessServiceController {

    private final BusinessServiceRegistryService businessServiceRegistryService;

    public BusinessServiceController(BusinessServiceRegistryService businessServiceRegistryService) {
        this.businessServiceRegistryService = businessServiceRegistryService;
    }

    @GetMapping("/summary")
    @Operation(summary = "查询业务应用服务概览")
    public ResponseWrap<BusinessServiceSummaryVo> summary() {
        return ResponseWrap.success(businessServiceRegistryService.summary());
    }

    @GetMapping("/instances")
    @Operation(summary = "分页查询业务应用服务实例")
    public ResponseWrap<PageRowsVo<BusinessServiceInstanceVo>> instances(
            BusinessServiceInstanceSearchDto search) {
        return ResponseWrap.success(businessServiceRegistryService.getInstancePage(search));
    }

    @GetMapping("/instances/{id}")
    @Operation(summary = "查询业务应用服务实例详情")
    public ResponseWrap<BusinessServiceInstanceVo> instance(@PathVariable("id") Integer id) {
        return ResponseWrap.success(businessServiceRegistryService.getInstance(id));
    }

    @GetMapping("/events")
    @Operation(summary = "分页查询业务应用服务事件")
    public ResponseWrap<PageRowsVo<BusinessServiceEventVo>> events(BusinessServiceEventSearchDto search) {
        return ResponseWrap.success(businessServiceRegistryService.getEventPage(search));
    }
}
