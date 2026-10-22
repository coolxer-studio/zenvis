package com.coolxer.plugin.lab.controller;

import com.coolxer.plugin.lab.api.PageRows;
import com.coolxer.plugin.lab.api.ResponseWrap;
import com.coolxer.plugin.lab.model.LabNote;
import com.coolxer.plugin.lab.model.LabNoteRequests;
import com.coolxer.plugin.lab.model.LabNoteStats;
import com.coolxer.plugin.lab.service.LabNoteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notes")
public class LabNoteController {

    private final LabNoteService service;

    public LabNoteController(LabNoteService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseWrap<PageRows<LabNote>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPage", defaultValue = "20") int perPage,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status) {
        return ResponseWrap.success(service.search(page, perPage, keyword, status));
    }

    @GetMapping("/stats")
    public ResponseWrap<LabNoteStats> stats() {
        return ResponseWrap.success(service.stats());
    }

    @GetMapping("/{id}")
    public ResponseWrap<LabNote> detail(@PathVariable("id") long id) {
        return ResponseWrap.success(service.detail(id));
    }

    @PostMapping
    public ResponseWrap<LabNote> create(@RequestBody(required = false) LabNoteRequests.Upsert request) {
        return ResponseWrap.success("创建成功", service.create(request));
    }

    @PostMapping("/{id}/update")
    public ResponseWrap<LabNote> update(@PathVariable("id") long id,
                                        @RequestBody(required = false) LabNoteRequests.Upsert request) {
        return ResponseWrap.success("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseWrap<Object> delete(@PathVariable("id") long id) {
        service.delete(id);
        return ResponseWrap.success("删除成功", null);
    }
}
