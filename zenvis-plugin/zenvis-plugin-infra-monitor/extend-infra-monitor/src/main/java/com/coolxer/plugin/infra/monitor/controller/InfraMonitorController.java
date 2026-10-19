package com.coolxer.plugin.infra.monitor.controller;

import com.coolxer.plugin.infra.monitor.model.ApiResponse;
import com.coolxer.plugin.infra.monitor.model.MonitorOverview;
import com.coolxer.plugin.infra.monitor.service.InfraMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/endpoints")
public class InfraMonitorController {

    private final InfraMetricsService service;

    @Autowired
    public InfraMonitorController(InfraMetricsService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<MonitorOverview> overview() {
        return ApiResponse.success(service.overview());
    }
}
