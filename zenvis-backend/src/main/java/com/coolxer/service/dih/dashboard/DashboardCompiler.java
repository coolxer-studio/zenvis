package com.coolxer.service.dih.dashboard;

import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.utils.JacksonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic HTML and AMIS compiler. Model input is limited to DashboardSpec. */
@Service
public class DashboardCompiler {

    private static final String API_PREFIX = "/api/v1";
    private static final String ECHARTS_SCRIPT = "/echarts/echarts.min.js";
    private static final String AMIS_SDK = "/amis";

    public CompiledFiles compile(DashboardSpec spec, boolean preview) {
        if (spec == null) {
            throw new IllegalArgumentException("大屏规格不能为空");
        }
        if (DashboardSupport.htmlMode(spec)) {
            return new CompiledFiles(Map.of(DashboardSupport.htmlPath(spec), html(spec)), "HTML_PAGE");
        }
        return new CompiledFiles(Map.of("index.json", JacksonUtil.toJson(amis(spec))), "LOW_CODE_PAGE");
    }

    public Map<String, String> previewFiles(DashboardSpec spec, String previewFilesApi) {
        Map<String, String> files = new LinkedHashMap<>();
        if (DashboardSupport.htmlMode(spec)) {
            files.put(DashboardSupport.htmlPath(spec), html(spec));
            return files;
        }
        files.put("preview.html", lowCodePreviewWrapper(previewFilesApi));
        files.put("site.json", JacksonUtil.toJson(site(spec, previewFilesApi)));
        files.put("index.json", JacksonUtil.toJson(amis(spec)));
        return files;
    }

    public String previewContentPath(DashboardSpec spec) {
        return DashboardSupport.htmlMode(spec) ? DashboardSupport.htmlPath(spec) : "preview.html";
    }

    private Map<String, Object> site(DashboardSpec spec, String previewFilesApi) {
        String schemaApi = "get:" + previewFilesApi + (previewFilesApi.contains("?") ? "&" : "?")
                + "path=index.json";
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("label", spec.getName());
        page.put("url", spec.getDashboardId());
        page.put("schemaApi", schemaApi);
        Map<String, Object> home = new LinkedHashMap<>();
        home.put("label", "Home");
        home.put("url", "/");
        home.put("redirect", "/" + spec.getDashboardId());
        return Map.of("status", 0, "msg", "", "data", Map.of("pages", List.of(home, page)));
    }

    private String lowCodePreviewWrapper(String previewFilesApi) {
        String siteApi = previewFilesApi + (previewFilesApi.contains("?") ? "&" : "?")
                + "path=site.json";
        return """
                <!doctype html>
                <html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <style>html,body,iframe{width:100%;height:100%;margin:0;border:0;background:#f6f8fc}</style></head>
                <body><iframe id="preview" title="低代码大屏预览" sandbox="allow-scripts allow-same-origin allow-forms"></iframe>
                <script>(function(){const bases=['/zenvis',''];const siteApi=__SITE_API__;let index=0;
                function load(){const base=bases[index++];const iframe=document.getElementById('preview');
                const url=base+'/amis/app.html?siteApi='+encodeURIComponent(siteApi)+'&baseUrl='+encodeURIComponent(base);
                iframe.onerror=function(){if(index<bases.length)load();};iframe.src=url;}load();})();</script>
                </body></html>
                """.replace("__SITE_API__", json(siteApi));
    }

    private String html(DashboardSpec spec) {
        String payload = json(spec);
        StringBuilder panels = new StringBuilder();
        Map<String, DashboardSpec.DashboardBlock> blocks = new LinkedHashMap<>();
        spec.getBlocks().forEach(block -> blocks.put(block.getBlockId(), block));
        for (DashboardSpec.DashboardRow row : spec.getLayout().getRows()) {
            panels.append("<section class=\"dashboard-row\" data-row=\"")
                    .append(escapeHtml(row.getRowId())).append("\">");
            for (DashboardSpec.DashboardLayoutItem item : row.getBlocks()) {
                DashboardSpec.DashboardBlock block = blocks.get(item.getBlockId());
                if (block == null) {
                    continue;
                }
                panels.append(panel(block, item.getSpan()));
            }
            panels.append("</section>");
        }
        String timeOptions = spec.getAllowedTimeRanges().stream()
                .map(preset -> "<option value=\"" + escapeHtml(preset) + "\""
                        + (preset.equals(spec.getDefaultTimeRange()) ? " selected" : "") + ">"
                        + escapeHtml(timePresetLabel(preset)) + "</option>")
                .toList().stream().reduce("", String::concat);
        return HTML_TEMPLATE
                .replace("__TITLE__", escapeHtml(spec.getName()))
                .replace("__DESCRIPTION__", escapeHtml(StringUtils.defaultIfBlank(spec.getDescription(), "实时数据态势总览")))
                .replace("__DASHBOARD_ID__", escapeHtml(spec.getDashboardId()))
                .replace("__TIME_OPTIONS__", timeOptions)
                .replace("__PANELS__", panels.toString())
                .replace("__REFRESH_SECONDS__", String.valueOf(spec.getRefreshIntervalSeconds()))
                .replace("__REFRESH_LABEL__", spec.getRefreshIntervalSeconds() + " 秒自动刷新")
                .replace("__PAYLOAD__", payload);
    }

    private String panel(DashboardSpec.DashboardBlock block, int span) {
        StringBuilder body = new StringBuilder();
        if ("metrics".equals(block.getType())) {
            body.append("<div class=\"kpi-grid\">");
            for (DashboardSpec.DashboardMetricCard card : block.getMetricCards()) {
                body.append("<article class=\"kpi\"><div class=\"kpi-label\">")
                        .append(escapeHtml(card.getLabel()))
                        .append("</div><div class=\"kpi-value\" data-metric-field=\"")
                        .append(escapeHtml(card.getField())).append("\">—</div><div class=\"kpi-note\">等待实时数据</div></article>");
            }
            body.append("</div>");
        } else if ("chart".equals(block.getType())) {
            body.append("<div class=\"chart-canvas\" data-chart=\"")
                    .append(escapeHtml(block.getBlockId())).append("\" role=\"img\" aria-label=\"")
                    .append(escapeHtml(block.getTitle())).append("\"></div>");
        } else if ("records".equals(block.getType())) {
            body.append(recordsPanel(block));
        }
        return "<article class=\"panel panel--" + escapeHtml(block.getType())
                + "\" style=\"grid-column:span " + Math.max(1, Math.min(12, span))
                + "\" data-block-id=\"" + escapeHtml(block.getBlockId()) + "\">"
                + "<div class=\"panel-error\" hidden></div>"
                + "<header class=\"panel-head\"><div><h2>" + escapeHtml(block.getTitle())
                + "</h2><p>" + escapeHtml(StringUtils.defaultIfBlank(block.getDescription(), blockTypeLabel(block.getType())))
                + "</p></div><span class=\"panel-meta\">" + escapeHtml(blockTypeLabel(block.getType()))
                + "</span></header><div class=\"panel-body\">" + body + "</div></article>";
    }

    private String recordsPanel(DashboardSpec.DashboardBlock block) {
        DashboardSpec.DashboardRecords records = block.getRecords();
        StringBuilder filters = new StringBuilder();
        for (int index = 0; index < records.getFilters().size(); index++) {
            DashboardSpec.DashboardRecordFilter filter = records.getFilters().get(index);
            filters.append("<label class=\"filter-field\"><span>")
                    .append(escapeHtml(StringUtils.defaultIfBlank(filter.getLabel(), filter.getField())))
                    .append("</span><input type=\"text\" data-filter-index=\"")
                    .append(index).append("\" data-filter-field=\"")
                    .append(escapeHtml(filter.getField())).append("\" placeholder=\"筛选 ")
                    .append(escapeHtml(filter.getField())).append("\"></label>");
        }
        StringBuilder columns = new StringBuilder();
        for (DashboardSpec.DashboardRecordColumn column : records.getColumns()) {
            columns.append("<th>").append(escapeHtml(StringUtils.defaultIfBlank(column.getLabel(), column.getField())))
                    .append("</th>");
        }
        return "<div class=\"records\"><form class=\"record-filters\" data-record-form=\""
                + escapeHtml(block.getBlockId()) + "\">" + filters
                + "<button type=\"submit\">查询</button></form><div class=\"table-wrap\"><table><thead><tr>"
                + columns + "</tr></thead><tbody data-record-rows=\"" + escapeHtml(block.getBlockId())
                + "\"></tbody></table></div><footer class=\"record-footer\"><span data-record-total=\""
                + escapeHtml(block.getBlockId()) + "\">共 — 条</span><div><button type=\"button\" data-page-prev=\""
                + escapeHtml(block.getBlockId()) + "\">上一页</button><button type=\"button\" data-page-next=\""
                + escapeHtml(block.getBlockId()) + "\">下一页</button></div></footer></div>";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> amis(DashboardSpec spec) {
        List<Map<String, Object>> columns = new ArrayList<>();
        Map<String, DashboardSpec.DashboardBlock> blocks = new LinkedHashMap<>();
        spec.getBlocks().forEach(block -> blocks.put(block.getBlockId(), block));
        for (DashboardSpec.DashboardRow row : spec.getLayout().getRows()) {
            for (DashboardSpec.DashboardLayoutItem item : row.getBlocks()) {
                DashboardSpec.DashboardBlock block = blocks.get(item.getBlockId());
                if (block != null) {
                    columns.add(Map.of(
                            "md", Math.max(1, Math.min(12, item.getSpan())),
                            "body", amisBlock(block)));
                }
            }
        }
        List<Object> timeOptions = spec.getAllowedTimeRanges().stream()
                .<Object>map(preset -> Map.of("label", timePresetLabel(preset), "value", preset))
                .toList();
        Map<String, Object> timeForm = new LinkedHashMap<>();
        timeForm.put("type", "form");
        timeForm.put("wrapWithPanel", false);
        timeForm.put("mode", "inline");
        timeForm.put("className", "dashboard-amis-filters");
        timeForm.put("body", List.of(Map.of(
                "type", "select", "name", "time_preset", "label", "时间范围",
                "value", spec.getDefaultTimeRange(), "options", timeOptions)));
        timeForm.put("actions", List.of(Map.of("type", "submit", "label", "刷新", "level", "primary")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "page");
        result.put("title", spec.getName());
        result.put("className", "dashboard-amis-page");
        result.put("data", Map.of("time_preset", spec.getDefaultTimeRange()));
        result.put("body", List.of(timeForm, Map.of("type", "grid", "columns", columns)));
        return result;
    }

    private Map<String, Object> amisBlock(DashboardSpec.DashboardBlock block) {
        return switch (block.getType()) {
            case "metrics" -> amisMetrics(block);
            case "chart" -> amisChart(block);
            case "records" -> amisRecords(block);
            default -> Map.of("type", "tpl", "tpl", escapeHtml(block.getTitle()));
        };
    }

    private Map<String, Object> amisMetrics(DashboardSpec.DashboardBlock block) {
        String tool = block.getQuery().getTool();
        Map<String, Object> request = withTime(block.getQuery().getRequest());
        String url = API_PREFIX + ("entity_overview".equals(tool)
                ? "/entity/overview/query" : "/entity/summary/query");
        List<Map<String, Object>> cards = new ArrayList<>();
        StringBuilder adaptor = new StringBuilder("var d=payload&&payload.data?payload.data:{};"
                + "var r=d.result&&d.result.rows&&d.result.rows[0]?d.result.rows[0]:{};var out={};");
        for (DashboardSpec.DashboardMetricCard card : block.getMetricCards()) {
            String field = safeJsName(card.getField());
            adaptor.append("out.").append(field).append("=r[").append(json(card.getField())).append("];");
            String expression = "${" + field + "}";
            if (StringUtils.isNotBlank(card.getPrefix())) {
                expression = escapeHtml(card.getPrefix()) + expression;
            }
            if (StringUtils.isNotBlank(card.getSuffix())) {
                expression = expression + escapeHtml(card.getSuffix());
            }
            cards.add(Map.of("md", Math.max(3, 12 / Math.max(1, block.getMetricCards().size())),
                    "body", Map.of("type", "panel", "title", card.getLabel(),
                            "body", "<div style='font-size:clamp(22px,2vw,34px);font-weight:750;color:#41e6db'>"
                                    + expression + "</div>")));
        }
        adaptor.append("return out;");
        Map<String, Object> api = new LinkedHashMap<>();
        api.put("method", "post");
        api.put("url", url);
        api.put("data", request);
        api.put("adaptor", adaptor.toString());
        return Map.of("type", "panel", "title", block.getTitle(),
                "body", Map.of("type", "service", "api", api,
                        "body", Map.of("type", "grid", "columns", cards)));
    }

    private Map<String, Object> amisChart(DashboardSpec.DashboardBlock block) {
        String tool = block.getQuery().getTool();
        String url = API_PREFIX + switch (tool) {
            case "entity_trend" -> "/entity/trend/query";
            case "entity_distribution" -> "/entity/distribution/query";
            case "entity_aggregate" -> "/entity/aggregate/query";
            case "entity_histogram" -> "/entity/histogram/query";
            case "entity_scatter" -> "/entity/scatter/query";
            default -> throw new IllegalArgumentException("不支持的图表查询工具：" + tool);
        };
        Map<String, Object> api = new LinkedHashMap<>();
        api.put("method", "post");
        api.put("url", url);
        api.put("data", withTime(block.getQuery().getRequest()));
        api.put("adaptor", "var d=payload&&payload.data?payload.data:{};"
                + "var o=d.echarts&&d.echarts.option?d.echarts.option:{};"
                + "return {status:0,msg:'',data:{chart_dataset:o.dataset||{source:[]},"
                + "chart_series:o.series||[]}};");
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", "chart");
        chart.put("api", api);
        chart.put("height", 360);
        chart.put("config", Map.of(
                "dataset", "${chart_dataset}",
                "series", "${chart_series}",
                "backgroundColor", "transparent",
                "textStyle", Map.of("color", "#dcecff")));
        return Map.of("type", "panel", "title", block.getTitle(),
                "body", chart);
    }

    private Map<String, Object> amisRecords(DashboardSpec.DashboardBlock block) {
        DashboardSpec.DashboardRecords records = block.getRecords();
        List<Map<String, Object>> columns = new ArrayList<>();
        Set<String> fields = new java.util.LinkedHashSet<>();
        for (DashboardSpec.DashboardRecordColumn column : records.getColumns()) {
            fields.add(column.getField());
            columns.add(Map.of("name", column.getField(),
                    "label", StringUtils.defaultIfBlank(column.getLabel(), column.getField())));
        }
        if (!fields.contains("zenvis_id")) {
            columns.add(Map.of("name", "zenvis_id", "type", "hidden"));
            fields.add("zenvis_id");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "normal");
        data.put("entity", records.getEntity());
        data.put("criteria_logic", "and");
        data.put("criteria_list", List.of());
        data.put("display_list", List.of(Map.of("entity", records.getEntity(),
                "attribute_list", new ArrayList<>(fields))));
        data.put("page", "${page}");
        data.put("size", "${perPage}");
        data.put("sortBy", StringUtils.defaultIfBlank(records.getDefaultSortField(), ""));
        data.put("order", records.getDefaultSortDirection());
        Map<String, Object> api = new LinkedHashMap<>();
        api.put("method", "post");
        api.put("url", API_PREFIX + "/retrieval/do");
        api.put("data", data);
        api.put("adaptor", "var d=payload&&payload.data?payload.data:(payload||{});"
                + "var rows=d.datalist||d.rows||[];return {status:0,msg:'',"
                + "data:{items:rows,total:Number(d.total||rows.length||0)}};");
        Map<String, Object> crud = new LinkedHashMap<>();
        crud.put("type", "crud");
        crud.put("title", block.getTitle());
        crud.put("api", api);
        crud.put("columns", columns);
        crud.put("perPage", records.getPageSize());
        crud.put("pageField", "page");
        crud.put("perPageField", "perPage");
        crud.put("headerToolbar", List.of());
        crud.put("bulkActions", List.of());
        crud.put("itemActions", List.of());
        crud.put("placeholder", "当前筛选条件下暂无数据");
        return Map.of("type", "panel", "title", block.getTitle(), "body", crud);
    }

    private Map<String, Object> withTime(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>(input == null ? Map.of() : input);
        Map<String, Object> timeRange = new LinkedHashMap<>();
        timeRange.put("preset", "${time_preset}");
        timeRange.put("start_time", null);
        timeRange.put("end_time", null);
        result.put("time_range", timeRange);
        return result;
    }

    private String safeJsName(String value) {
        String cleaned = StringUtils.defaultIfBlank(value, "metric")
                .replaceAll("[^A-Za-z0-9_$]", "_");
        return Character.isJavaIdentifierStart(cleaned.charAt(0)) ? cleaned : "_" + cleaned;
    }

    private String blockTypeLabel(String type) {
        return switch (type) {
            case "metrics" -> "关键指标";
            case "chart" -> "可视化分析";
            case "records" -> "只读明细";
            default -> "数据区块";
        };
    }

    private String timePresetLabel(String preset) {
        return switch (preset) {
            case "LAST_24_HOURS" -> "近 24 小时";
            case "LAST_7_DAYS" -> "近 7 天";
            case "LAST_30_DAYS" -> "近 30 天";
            case "THIS_MONTH" -> "本月";
            case "ALL_TIME" -> "全部时间";
            default -> preset;
        };
    }

    private String json(Object value) {
        String serialized = JacksonUtil.toJson(value);
        return Objects.toString(serialized, "null")
                .replace("&", "\\u0026")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    private String escapeHtml(String value) {
        return StringUtils.defaultString(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public record CompiledFiles(Map<String, String> files, String dashboardType) {
    }

    private static final String HTML_TEMPLATE = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
              <meta name="color-scheme" content="dark">
              <title>__TITLE__</title>
              <style>
                :root { color-scheme: dark; --bg:#040b18; --panel:rgba(9,24,46,.9); --line:rgba(75,166,255,.22); --cyan:#42e8e0; --blue:#4da3ff; --text:#eef8ff; --muted:#8da8c9; --good:#62e6a7; --warn:#ffc66d; --bad:#ff7895; }
                *{box-sizing:border-box} html,body{width:100%;min-height:100%;margin:0} body{overflow-x:hidden;background:radial-gradient(circle at 50% -12%,rgba(64,146,255,.28),transparent 40%),radial-gradient(circle at 100% 88%,rgba(66,232,224,.08),transparent 32%),linear-gradient(rgba(80,147,225,.035) 1px,transparent 1px),linear-gradient(90deg,rgba(80,147,225,.035) 1px,transparent 1px),var(--bg);background-size:auto,auto,40px 40px,40px 40px,auto;color:var(--text);font-family:Inter,"PingFang SC","Microsoft YaHei",sans-serif}
                button,input,select{font:inherit} button{cursor:pointer}
                .dashboard-shell{width:100%;max-width:100vw;min-height:100vh;padding:clamp(12px,1.4vw,24px);display:flex;flex-direction:column;gap:clamp(10px,1vw,16px)}
                .topbar{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);gap:clamp(10px,1.4vw,22px);align-items:center;min-width:0}
                .brand,.top-actions{min-width:0;height:52px;display:flex;align-items:center;gap:10px;padding:0 14px;border:1px solid var(--line);border-radius:9px;background:linear-gradient(90deg,rgba(19,43,78,.84),rgba(8,21,41,.72));box-shadow:inset 0 1px rgba(255,255,255,.04)}
                .brand-mark{width:26px;height:26px;flex:0 0 auto;border:2px solid var(--cyan);border-radius:8px 3px 8px 3px;transform:rotate(45deg);box-shadow:0 0 18px rgba(66,232,224,.35)}
                .brand-copy{min-width:0}.brand-copy strong{display:block;font-size:13px}.brand-copy span{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--muted);font-size:11px}
                .title{text-align:center;min-width:0}.title h1{margin:0;font-size:clamp(20px,2.1vw,36px);letter-spacing:clamp(1px,.32vw,5px);line-height:1.15;text-shadow:0 0 24px rgba(77,163,255,.45);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.title p{margin:4px 0 0;color:var(--muted);font-size:11px;letter-spacing:1.5px}
                .top-actions{justify-content:flex-end;flex-wrap:nowrap}.top-actions select{min-width:110px;max-width:142px;border:1px solid rgba(77,163,255,.4);border-radius:6px;background:#0b1e39;color:#dcecff;padding:7px 9px}.refresh{border:1px solid rgba(77,163,255,.46);border-radius:6px;background:rgba(39,100,185,.24);color:#dcecff;padding:7px 11px;white-space:nowrap}.refresh:disabled{opacity:.55;cursor:wait}.status{color:var(--muted);font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:180px}
                .alert{display:none;align-items:center;justify-content:space-between;gap:12px;padding:9px 12px;border:1px solid rgba(255,120,149,.42);border-radius:8px;background:rgba(93,22,42,.32);color:#ffd0da;font-size:12px}.alert.visible{display:flex}.alert button{border:0;background:transparent;color:inherit;text-decoration:underline}
                .dashboard-grid{display:flex;flex-direction:column;gap:clamp(10px,1vw,16px);min-width:0}.dashboard-row{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));gap:clamp(10px,1vw,16px);min-width:0}.panel{position:relative;min-width:0;min-height:0;padding:clamp(12px,1vw,18px);border:1px solid var(--line);border-radius:11px;background:linear-gradient(155deg,rgba(12,31,58,.95),rgba(6,19,38,.9));box-shadow:0 16px 38px rgba(0,0,0,.23),inset 0 1px rgba(255,255,255,.04);overflow:hidden}.panel::before{content:"";position:absolute;inset:0 auto auto 0;width:84px;height:2px;background:linear-gradient(90deg,var(--blue),var(--cyan),transparent)}.panel--chart{min-height:320px}.panel--records{min-height:360px}.panel-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin-bottom:12px;min-width:0}.panel-head h2{margin:0;font-size:clamp(13px,1.05vw,17px);line-height:1.35}.panel-head p{margin:4px 0 0;color:var(--muted);font-size:11px;line-height:1.4}.panel-meta{flex:0 0 auto;color:var(--muted);font-size:10px;border:1px solid rgba(95,165,255,.18);border-radius:999px;padding:3px 8px}.panel-body{min-width:0}.panel-error{display:none;position:absolute;inset:0;padding:52px 18px 18px;background:rgba(6,15,30,.92);z-index:4;color:#ffd3dc;font-size:12px}.panel-error.visible{display:block}.panel-error strong{display:block;margin-bottom:6px;font-size:14px}
                .kpi-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:10px}.kpi{min-width:0;padding:12px;border:1px solid rgba(95,165,255,.14);border-radius:8px;background:rgba(14,39,70,.48)}.kpi-label{color:var(--muted);font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.kpi-value{margin-top:8px;font-size:clamp(22px,2vw,36px);font-weight:780;line-height:1;font-variant-numeric:tabular-nums;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-shadow:0 0 18px rgba(66,232,224,.18)}.kpi-note{margin-top:7px;color:var(--muted);font-size:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
                .chart-canvas{width:100%;height:clamp(240px,27vh,480px);min-width:0}.chart-empty,.records-empty{height:100%;min-height:180px;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:12px;border:1px dashed rgba(95,165,255,.2);border-radius:8px}.records{min-width:0}.record-filters{display:flex;flex-wrap:wrap;align-items:flex-end;gap:8px;margin-bottom:10px}.filter-field{display:flex;flex-direction:column;gap:4px;min-width:120px;color:var(--muted);font-size:10px}.filter-field input{min-width:120px;max-width:180px;border:1px solid rgba(95,165,255,.24);border-radius:6px;background:rgba(6,20,40,.9);color:var(--text);padding:6px 8px}.record-filters button,.record-footer button{border:1px solid rgba(77,163,255,.38);border-radius:6px;background:rgba(39,100,185,.2);color:#dcecff;padding:6px 10px}.table-wrap{width:100%;overflow:hidden;border:1px solid rgba(95,165,255,.14);border-radius:8px}.records table{width:100%;table-layout:fixed;border-collapse:collapse;font-size:11px}.records th,.records td{padding:8px 9px;text-align:left;border-bottom:1px solid rgba(95,165,255,.1);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.records th{color:#9fe8ff;background:rgba(30,74,120,.26);font-weight:600}.records tr:last-child td{border-bottom:0}.record-footer{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:9px;color:var(--muted);font-size:11px}.record-footer div{display:flex;gap:6px}.record-footer button:disabled{opacity:.4;cursor:not-allowed}
                .loading{display:none;position:fixed;inset:0;z-index:20;align-items:center;justify-content:center;background:rgba(4,11,24,.42);backdrop-filter:blur(2px)}.loading.visible{display:flex}.loading-card{display:flex;align-items:center;gap:10px;padding:12px 16px;border:1px solid var(--line);border-radius:9px;background:#0b1e39;color:#dcecff;font-size:12px}.spinner{width:17px;height:17px;border:2px solid rgba(77,163,255,.28);border-top-color:var(--cyan);border-radius:50%;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media (prefers-reduced-motion:reduce){.spinner{animation:none}}
                @media (max-width:1500px){.dashboard-shell{padding:12px}.status{max-width:110px}.top-actions{gap:7px;padding:0 9px}.top-actions select{min-width:96px;max-width:116px}.brand,.top-actions{height:48px}.panel{padding:12px}.chart-canvas{height:clamp(220px,29vh,400px)}}
                @media (max-width:1100px){.topbar{grid-template-columns:1fr}.brand{display:none}.top-actions{order:2;justify-content:center}.status{display:none}.title{order:1}.dashboard-row .panel{grid-column:span 12!important}.header-side{display:none}}
              </style>
            </head>
            <body>
              <main class="dashboard-shell" data-dashboard-id="__DASHBOARD_ID__">
                <header class="topbar">
                  <div class="brand"><span class="brand-mark"></span><div class="brand-copy"><strong>ZENVIS · LIVE DATA</strong><span>__DESCRIPTION__</span></div></div>
                  <div class="title"><h1>__TITLE__</h1><p>READ-ONLY BUSINESS OBSERVATORY</p></div>
                  <div class="top-actions"><select id="timePreset" aria-label="时间范围">__TIME_OPTIONS__</select><button id="refresh" class="refresh" type="button">刷新</button><span id="updatedAt" class="status">等待更新</span></div>
                </header>
                <div id="globalError" class="alert" role="alert"><span id="globalErrorMessage"></span><button id="retry" type="button">重试</button></div>
                <div id="dashboardGrid" class="dashboard-grid">__PANELS__</div>
                <div id="loading" class="loading" role="status" aria-live="polite"><div class="loading-card"><span class="spinner"></span><span>正在加载真实业务数据…</span></div></div>
              </main>
              <script id="dashboard-payload" type="application/json">__PAYLOAD__</script>
              <script>
                (() => {
                  const SPEC = JSON.parse(document.getElementById('dashboard-payload').textContent);
                  const API_BASES = ['/zenvis/api/v1', '/api/v1'];
                  const TOOL_PATHS = {
                    entity_overview: '/entity/overview/query', entity_summary: '/entity/summary/query',
                    entity_trend: '/entity/trend/query', entity_distribution: '/entity/distribution/query',
                    entity_aggregate: '/entity/aggregate/query', entity_histogram: '/entity/histogram/query',
                    entity_scatter: '/entity/scatter/query'
                  };
                  const BLOCKS = new Map(SPEC.blocks.map(block => [block.block_id, block]));
                  const CHART_INSTANCES = new Map();
                  const RECORD_PAGES = new Map();
                  let apiBase = API_BASES[0];
                  let refreshTimer;
                  let echartsPromise;
                  let loading = false;
                  const $ = id => document.getElementById(id);
                  const elements = {refresh: $('refresh'), retry: $('retry'), loading: $('loading'),
                    updated: $('updatedAt'), error: $('globalError'), errorMessage: $('globalErrorMessage'),
                    preset: $('timePreset'), grid: $('dashboardGrid')};
                  const number = value => Number.isFinite(Number(value)) ? Number(value) : 0;
                  const formatNumber = (value, precision) => number(value).toLocaleString('zh-CN', {maximumFractionDigits: precision == null ? 2 : precision, minimumFractionDigits: precision == null ? 0 : precision});
                  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
                  const unwrap = value => value && Object.prototype.hasOwnProperty.call(value, 'data') ? value.data : value;
                  const post = body => ({method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
                  const panel = id => document.querySelector('[data-block-id="' + CSS.escape(id) + '"]');
                  const blockError = (id, message) => { const target = panel(id); if (!target) return; const error = target.querySelector('.panel-error'); error.textContent = ''; const strong = document.createElement('strong'); strong.textContent = '该区块加载失败'; const detail = document.createElement('div'); detail.textContent = message || '未知错误'; error.append(strong, detail); error.classList.add('visible'); };
                  const clearBlockError = id => { const target = panel(id); if (target) target.querySelector('.panel-error').classList.remove('visible'); };

                  async function request(path, options = {}) {
                    let lastError;
                    for (const base of [apiBase, ...API_BASES.filter(item => item !== apiBase)]) {
                      try {
                        const response = await fetch(base + path, {credentials: 'same-origin', ...options});
                        if (!response.ok) throw new Error('HTTP ' + response.status);
                        const json = await response.json();
                        const status = json && json.status !== undefined ? json.status : (json && json.code);
                        if (status !== undefined && ![0, 200].includes(Number(status))) throw new Error(json.msg || json.message || ('接口返回 ' + status));
                        apiBase = base;
                        return unwrap(json);
                      } catch (error) { lastError = error; }
                    }
                    throw lastError || new Error('接口请求失败');
                  }

                  function analyticsBody(block, preset) {
                    const body = JSON.parse(JSON.stringify(block.query.request || {}));
                    const tool = block.query.tool;
                    if (tool === 'entity_overview') {
                      if (!Array.isArray(body.entities) || !body.entities.length) body.entities = block.query.entity ? [block.query.entity] : [];
                    } else if (!body.entity && block.query.entity) body.entity = block.query.entity;
                    if (tool !== 'entity_trend' && SPEC.time_field) body.time_field = body.time_field || SPEC.time_field;
                    if (tool === 'entity_trend' && Array.isArray(body.series)) body.series.forEach(series => { if (SPEC.time_field && !series.time_field) series.time_field = SPEC.time_field; });
                    body.time_range = {preset, start_time: null, end_time: null};
                    if (tool === 'entity_aggregate' && Array.isArray(body.dimensions)) body.dimensions.forEach(dimension => { if (String(dimension.kind || '').toUpperCase() === 'TIME' && !body.time_field) body.time_field = SPEC.time_field; });
                    return body;
                  }

                  function loadEcharts() {
                    if (window.echarts) return Promise.resolve(window.echarts);
                    if (echartsPromise) return echartsPromise;
                    const candidates = ['/zenvis' + '__ECHARTS__', '__ECHARTS__'];
                    echartsPromise = new Promise((resolve, reject) => {
                      let index = 0;
                      const next = () => {
                        if (index >= candidates.length) { reject(new Error('ECharts 运行时加载失败')); return; }
                        const script = document.createElement('script');
                        script.src = candidates[index++]; script.async = true;
                        script.onload = () => window.echarts ? resolve(window.echarts) : next();
                        script.onerror = next; document.head.appendChild(script);
                      };
                      next();
                    });
                    return echartsPromise;
                  }

                  function chartOption(block, data) {
                    const serverOption = data && data.echarts && data.echarts.option;
                    const option = serverOption && typeof serverOption === 'object' ? JSON.parse(JSON.stringify(serverOption)) : {};
                    const rows = data && data.result && Array.isArray(data.result.rows) ? data.result.rows : [];
                    if (!rows.length) return null;
                    if (!option.series) {
                      const tool = block.query.tool;
                      if (tool === 'entity_scatter') {
                        option.series = [{type: 'scatter', data: rows.map(row => [row.x, row.y, row.size || row.value || 1])}];
                        option.xAxis = {type: 'value'}; option.yAxis = {type: 'value'};
                      } else {
                        const labels = rows.map(row => row.bucket ?? row.label ?? row.category ?? '');
                        const values = rows.map(row => number(row.value ?? row.count ?? row.total));
                        option.xAxis = {type: 'category', data: labels, axisLabel: {color: '#8da8c9'}, axisLine: {lineStyle: {color: 'rgba(95,165,255,.22)'}}};
                        option.yAxis = {type: 'value', axisLabel: {color: '#8da8c9'}, splitLine: {lineStyle: {color: 'rgba(95,165,255,.1)'}}};
                        option.series = [{type: tool === 'entity_distribution' ? 'bar' : 'line', data: values, smooth: tool === 'entity_trend', areaStyle: tool === 'entity_trend' ? {opacity: .14} : undefined}];
                      }
                    }
                    option.backgroundColor = 'transparent';
                    option.textStyle = {...(option.textStyle || {}), color: '#dcecff', fontFamily: 'Inter, PingFang SC, Microsoft YaHei, sans-serif'};
                    option.grid = {left: 46, right: 18, top: 22, bottom: 34, containLabel: true};
                    if (option.xAxis) option.xAxis.axisLabel = {...(option.xAxis.axisLabel || {}), color: '#8da8c9'};
                    if (option.yAxis) option.yAxis.axisLabel = {...(option.yAxis.axisLabel || {}), color: '#8da8c9'};
                    if (Array.isArray(option.series)) option.series.forEach(series => { series.itemStyle = {...(series.itemStyle || {}), borderRadius: series.type === 'bar' ? 4 : undefined}; });
                    return option;
                  }

                  async function renderMetrics(block, preset) {
                    const data = await request(TOOL_PATHS[block.query.tool], post(analyticsBody(block, preset)));
                    const rows = data && data.result && Array.isArray(data.result.rows) ? data.result.rows : [];
                    const row = rows[0] || {};
                    const rowsByMetric = new Map();
                    rows.forEach(item => {
                      const metric = item && (item.metric ?? item.name);
                      if (metric != null) rowsByMetric.set(String(metric), item);
                    });
                    const container = panel(block.block_id);
                    if (!container) return;
                    if (!rows.length) { container.querySelector('.panel-body').innerHTML = '<div class="chart-empty">当前时间范围暂无指标数据</div>'; return; }
                    (block.metric_cards || []).forEach(card => {
                      const node = container.querySelector('[data-metric-field="' + CSS.escape(card.field) + '"]');
                      if (!node) return;
                      const metricRow = rowsByMetric.get(String(card.field));
                      const value = metricRow
                        ? (metricRow.value ?? metricRow[card.field] ?? metricRow[card.field.toLowerCase()])
                        : (row[card.field] ?? row[card.field.toLowerCase()]);
                      node.textContent = value == null ? '—' : String(card.prefix || '') + formatNumber(value, card.precision) + String(card.suffix || '');
                      const note = node.parentElement.querySelector('.kpi-note');
                      if (note) note.textContent = '真实数据 · 自动刷新';
                    });
                  }

                  async function renderChart(block, preset) {
                    const data = await request(TOOL_PATHS[block.query.tool], post(analyticsBody(block, preset)));
                    const container = panel(block.block_id)?.querySelector('[data-chart]');
                    if (!container) return;
                    const option = chartOption(block, data);
                    if (!option) {
                      const stale = CHART_INSTANCES.get(block.block_id);
                      if (stale) { stale.dispose(); CHART_INSTANCES.delete(block.block_id); }
                      container.innerHTML = '<div class="chart-empty">当前时间范围暂无图表数据</div>'; return;
                    }
                    const echarts = await loadEcharts();
                    container.innerHTML = '';
                    let instance = CHART_INSTANCES.get(block.block_id);
                    if (!instance) { instance = echarts.init(container, null, {renderer: 'canvas'}); CHART_INSTANCES.set(block.block_id, instance); }
                    instance.setOption(option, true);
                    instance.resize();
                  }

                  function recordCriteria(block, preset, filters) {
                    const criteria = [];
                    const bounds = timeBounds(preset);
                    if (bounds && SPEC.time_field) criteria.push({attribute: SPEC.time_field, operator: 'between', value_list: [bounds.start, bounds.end]});
                    (block.records.filters || []).forEach((filter, index) => {
                      const value = String(filters[index] || '').trim();
                      if (!value) return;
                      criteria.push({attribute: filter.field, operator: filter.operator || 'equal', value_list: filter.operator === 'in' ? value.split(',').map(item => item.trim()).filter(Boolean) : [value]});
                    });
                    return criteria;
                  }

                  async function renderRecords(block, preset, page) {
                    const state = RECORD_PAGES.get(block.block_id) || {page: 1, filters: {}};
                    state.page = page || state.page || 1;
                    RECORD_PAGES.set(block.block_id, state);
                    const container = panel(block.block_id);
                    if (!container) return;
                    if (state.page === 1) {
                      const filters = {};
                      container.querySelectorAll('[data-filter-index]').forEach(input => { filters[input.dataset.filterIndex] = input.value; });
                      state.filters = filters;
                    }
                    const fields = (block.records.columns || []).map(column => column.field);
                    if (!fields.includes('zenvis_id')) fields.push('zenvis_id');
                    const body = {type: 'normal', entity: block.records.entity, criteria_logic: 'and',
                      criteria_list: recordCriteria(block, preset, state.filters),
                      display_list: [{entity: block.records.entity, attribute_list: fields}],
                      page: state.page, size: block.records.page_size || 12,
                      sortBy: block.records.default_sort_field || '', order: block.records.default_sort_direction || 'desc'};
                    const data = unwrap(await request('/retrieval/do', post(body))) || {};
                    const rows = Array.isArray(data.datalist) ? data.datalist : (Array.isArray(data.rows) ? data.rows : []);
                    const total = number(data.total);
                    const tbody = container.querySelector('[data-record-rows]');
                    tbody.innerHTML = '';
                    if (!rows.length) { const tr = document.createElement('tr'); const td = document.createElement('td'); td.colSpan = (block.records.columns || []).length || 1; td.innerHTML = '<div class="records-empty">当前筛选条件下暂无明细数据</div>'; tr.appendChild(td); tbody.appendChild(tr); }
                    rows.forEach(row => { const tr = document.createElement('tr'); (block.records.columns || []).forEach(column => { const td = document.createElement('td'); const value = row[column.field]; td.textContent = value == null || value === '' ? '—' : String(value); td.title = td.textContent; tr.appendChild(td); }); tbody.appendChild(tr); });
                    const footer = container.querySelector('[data-record-total]'); if (footer) footer.textContent = '共 ' + formatNumber(total, 0) + ' 条';
                    const prev = container.querySelector('[data-page-prev]'); const next = container.querySelector('[data-page-next]');
                    if (prev) prev.disabled = state.page <= 1;
                    if (next) next.disabled = state.page * (block.records.page_size || 12) >= total;
                  }

                  async function loadBlock(block, preset) {
                    clearBlockError(block.block_id);
                    try {
                      if (block.type === 'metrics') await renderMetrics(block, preset);
                      else if (block.type === 'chart') await renderChart(block, preset);
                      else if (block.type === 'records') await renderRecords(block, preset, 1);
                    } catch (error) {
                      blockError(block.block_id, error && error.message ? error.message : String(error));
                      throw error;
                    }
                  }

                  async function loadDashboard() {
                    if (loading) return;
                    loading = true; elements.loading.classList.add('visible'); elements.refresh.disabled = true; elements.error.classList.remove('visible');
                    const preset = elements.preset.value;
                    const blocks = Array.from(BLOCKS.values());
                    const results = await Promise.allSettled(blocks.map(block => loadBlock(block, preset)));
                    const failed = results.filter(result => result.status === 'rejected').length;
                    elements.updated.textContent = new Date().toLocaleTimeString('zh-CN', {hour12: false}) + (failed ? ' · 部分失败' : ' · 已更新');
                    if (!blocks.length) elements.error.classList.add('visible');
                    loading = false; elements.loading.classList.remove('visible'); elements.refresh.disabled = false;
                  }

                  function timeBounds(preset) {
                    if (preset === 'ALL_TIME') return null;
                    const end = new Date(); const start = new Date(end);
                    if (preset === 'LAST_24_HOURS') start.setHours(start.getHours() - 24);
                    else if (preset === 'LAST_7_DAYS') start.setDate(start.getDate() - 7);
                    else if (preset === 'LAST_30_DAYS') start.setDate(start.getDate() - 30);
                    else if (preset === 'THIS_MONTH') start.setDate(1);
                    start.setHours(0, 0, 0, 0);
                    const format = date => { const pad = value => String(value).padStart(2, '0'); return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds()); };
                    return {start: format(start), end: format(end)};
                  }

                  elements.refresh.addEventListener('click', loadDashboard);
                  elements.retry.addEventListener('click', loadDashboard);
                  elements.preset.addEventListener('change', loadDashboard);
                  elements.grid.addEventListener('submit', event => { const form = event.target.closest('[data-record-form]'); if (!form) return; event.preventDefault(); const block = BLOCKS.get(form.dataset.recordForm); if (block) { RECORD_PAGES.delete(block.block_id); loadBlock(block, elements.preset.value); } });
                  elements.grid.addEventListener('click', event => { const prev = event.target.closest('[data-page-prev]'); const next = event.target.closest('[data-page-next]'); const id = prev?.dataset.pagePrev || next?.dataset.pageNext; if (!id) return; const block = BLOCKS.get(id); if (!block) return; const state = RECORD_PAGES.get(id) || {page: 1}; const page = Math.max(1, state.page + (prev ? -1 : 1)); renderRecords(block, elements.preset.value, page).catch(error => blockError(id, error.message)); });
                  let resizeTimer; window.addEventListener('resize', () => { clearTimeout(resizeTimer); resizeTimer = setTimeout(() => CHART_INSTANCES.forEach(instance => instance.resize()), 160); });
                  document.addEventListener('visibilitychange', () => { if (!document.hidden) loadDashboard(); });
                  refreshTimer = setInterval(loadDashboard, Math.max(30, Number(SPEC.refresh_interval_seconds || 60)) * 1000);
                  loadDashboard();
                })();
              </script>
            </body>
            </html>
            """.replace("__ECHARTS__", ECHARTS_SCRIPT);
}
