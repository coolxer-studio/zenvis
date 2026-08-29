package com.coolxer.plugin.asset.controller;

import com.coolxer.plugin.asset.model.Asset;
import com.coolxer.plugin.asset.model.AssetRuleAction;
import com.coolxer.plugin.asset.model.AssetRuleDto;
import com.coolxer.plugin.asset.model.AssetRuleSearchQuery;
import com.coolxer.plugin.asset.model.AssetRuleStatus;
import com.coolxer.plugin.asset.model.AssetRuleView;
import com.coolxer.plugin.asset.service.AssetRuleService;
import com.coolxer.plugin.asset.api.PageRows;
import com.coolxer.plugin.asset.api.ResponseWrap;
import com.coolxer.plugin.asset.api.ResultCode;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rule")
public class AssetRuleController {

    private final AssetRuleService service;

    @Autowired
    public AssetRuleController(AssetRuleService service) {
        this.service = service;
    }

    @PostMapping("/add")
    public ResponseWrap<?> add(@Valid @RequestBody AssetRuleDto dto) {
        return service.add(dto)
                ? ResponseWrap.success("创建成功")
                : ResponseWrap.fail(ResultCode.UNKNOWN_ERROR);
    }

    @DeleteMapping("/{id}")
    public ResponseWrap<?> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseWrap.success("删除成功");
    }

    @DeleteMapping("/bulk/{ids}")
    public ResponseWrap<?> bulkDelete(@PathVariable List<Long> ids) {
        service.deleteAll(ids);
        return ResponseWrap.success("删除成功");
    }

    @PostMapping("/{id}/update")
    public ResponseWrap<?> update(@PathVariable long id, @Valid @RequestBody AssetRuleDto dto) {
        return service.update(id, dto) ? ResponseWrap.success("修改成功") : ResponseWrap.fail();
    }

    @PostMapping("/{ids}/bulk_update")
    public ResponseEntity<Void> bulkUpdate(
            @PathVariable Long[] ids,
            @RequestBody AssetRuleDto dto) {
        boolean updated = Arrays.stream(ids).allMatch(id -> service.update(id, dto));
        return updated ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @GetMapping("/list")
    public ResponseWrap<PageRows<AssetRuleView>> list(AssetRuleSearchQuery query) {
        return ResponseWrap.success(service.page(query));
    }

    @GetMapping("/{id}/view")
    public ResponseWrap<AssetRuleView> view(@PathVariable long id) {
        AssetRuleView view = service.get(id);
        return view == null ? ResponseWrap.fail() : ResponseWrap.success(view);
    }

    @PostMapping("/{id}/activate")
    public ResponseWrap<?> activate(@PathVariable long id) {
        return ResponseWrap.success("当前版本操作无效！");
    }

    @PostMapping("/{id}/deactivate")
    public ResponseWrap<?> deactivate(@PathVariable long id) {
        return ResponseWrap.success("当前版本操作无效！");
    }

    @GetMapping("/action/list")
    public ResponseWrap<?> actions() {
        return ResponseWrap.success(Map.of("options", Arrays.stream(AssetRuleAction.values())
                .map(value -> option(value.getDescription(), value.name()))
                .toList()));
    }

    @GetMapping("/asset/list")
    public ResponseWrap<?> assets() {
        return ResponseWrap.success(Map.of("options", Arrays.stream(Asset.values())
                .map(value -> option(value.getDescription(), value.name()))
                .toList()));
    }

    @GetMapping("/status/list")
    public ResponseWrap<?> statuses() {
        return ResponseWrap.success(Map.of("options", Arrays.stream(AssetRuleStatus.values())
                .map(value -> option(value.getDescription(), value.name()))
                .toList()));
    }

    private Map<String, String> option(String label, String value) {
        Map<String, String> option = new LinkedHashMap<>();
        option.put("label", label);
        option.put("value", value);
        return option;
    }
}
