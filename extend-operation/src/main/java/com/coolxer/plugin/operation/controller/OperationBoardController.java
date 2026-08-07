package com.coolxer.plugin.operation.controller;

import com.coolxer.plugin.operation.api.ResponseWrap;
import com.coolxer.plugin.operation.api.ResultCode;
import com.coolxer.plugin.operation.model.OperationBoardDto;
import com.coolxer.plugin.operation.service.OperationBoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OperationBoardController {

    private final OperationBoardService service;

    @Autowired
    public OperationBoardController(OperationBoardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public ResponseWrap<?> dashboard() {
        return ResponseWrap.success(Map.of("panel_list", service.getAll()));
    }

    @GetMapping("/dashboard/{id}/chart")
    public ResponseWrap<?> chart(@PathVariable long id) {
        return ResponseWrap.success(service.getChartById(id));
    }

    @PostMapping("/dashboard/add")
    public ResponseWrap<?> add(@RequestBody OperationBoardDto dto) {
        return service.add(dto)
                ? ResponseWrap.success("创建成功")
                : ResponseWrap.fail(ResultCode.UNKNOWN_ERROR);
    }

    @DeleteMapping("/{id}")
    public ResponseWrap<?> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseWrap.success("删除成功");
    }
}
