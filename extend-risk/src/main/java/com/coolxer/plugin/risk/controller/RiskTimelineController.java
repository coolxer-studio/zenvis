package com.coolxer.plugin.risk.controller;

import com.coolxer.plugin.risk.api.ResponseWrap;
import com.coolxer.plugin.risk.service.RiskTimelineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/timeline")
public class RiskTimelineController {

    private final RiskTimelineService service;

    @Autowired
    public RiskTimelineController(RiskTimelineService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseWrap<?> timeline(
            @RequestParam(value = "name", required = false) String name) {
        return ResponseWrap.success(service.timeline(name));
    }
}
