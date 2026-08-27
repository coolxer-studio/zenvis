package com.coolxer.controller.system;

import com.coolxer.aop.SkipRequestLog;
import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.system.dto.BusinessServiceEventDto;
import com.coolxer.model.system.dto.BusinessServiceHeartbeatDto;
import com.coolxer.model.system.vo.BusinessServiceEventAckVo;
import com.coolxer.model.system.vo.BusinessServiceHeartbeatAckVo;
import com.coolxer.service.system.BusinessServiceRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@SkipRequestLog
@SecurityRequirements
@Tag(name = "业务应用服务公开上报")
@RestController
@RequestMapping("/api/v1/public/business-services")
public class BusinessServicePublicController {

    private final BusinessServiceRegistryService businessServiceRegistryService;

    public BusinessServicePublicController(BusinessServiceRegistryService businessServiceRegistryService) {
        this.businessServiceRegistryService = businessServiceRegistryService;
    }

    @PostMapping("/heartbeat")
    @Operation(summary = "上报业务应用服务心跳", description = "公开接口，无需 Session 或 Bearer Token")
    public ResponseWrap<BusinessServiceHeartbeatAckVo> heartbeat(
            @Valid @RequestBody BusinessServiceHeartbeatDto dto,
            HttpServletRequest request) {
        log.debug("接收业务应用服务心跳: serviceCode={}, instanceId={}",
                dto.getServiceCode(), dto.getInstanceId());
        return ResponseWrap.success(
                businessServiceRegistryService.reportHeartbeat(dto, request.getRemoteAddr()));
    }

    @PostMapping("/events")
    @Operation(summary = "上报业务应用服务事件", description = "公开接口，无需 Session 或 Bearer Token")
    public ResponseWrap<BusinessServiceEventAckVo> event(
            @Valid @RequestBody BusinessServiceEventDto dto,
            HttpServletRequest request) {
        log.debug("接收业务应用服务事件: eventId={}, serviceCode={}, instanceId={}",
                dto.getEventId(), dto.getServiceCode(), dto.getInstanceId());
        return ResponseWrap.success(
                businessServiceRegistryService.reportEvent(dto, request.getRemoteAddr()));
    }
}
