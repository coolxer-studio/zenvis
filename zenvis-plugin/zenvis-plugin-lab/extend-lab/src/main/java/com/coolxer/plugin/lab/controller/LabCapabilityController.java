package com.coolxer.plugin.lab.controller;

import com.coolxer.plugin.lab.api.ResponseWrap;
import com.coolxer.plugin.lab.model.LabCapability;
import com.coolxer.plugin.lab.service.LabCapabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LabCapabilityController {

    private final LabCapabilityService service;

    public LabCapabilityController(LabCapabilityService service) {
        this.service = service;
    }

    @GetMapping("/capabilities")
    public ResponseWrap<List<LabCapability>> list() {
        return ResponseWrap.success(service.list());
    }
}
