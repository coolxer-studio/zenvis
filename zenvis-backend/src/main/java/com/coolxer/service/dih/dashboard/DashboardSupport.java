package com.coolxer.service.dih.dashboard;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.dashboard.DashboardSpec;
import com.coolxer.utils.JacksonUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DashboardSupport {

    private DashboardSupport() {
    }

    public static String canonicalJson(Object value) {
        return JacksonConfig.OBJECT_MAPPER.convertValue(value, Object.class) == null
                ? "null" : JacksonUtil.toJson(value);
    }

    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算数据大屏摘要", e);
        }
    }

    public static String digestObject(Object value) {
        return digest(canonicalJson(value));
    }

    public static boolean validDashboardId(String value) {
        return value != null && value.matches("[a-z][a-z0-9-]{1,62}[a-z0-9]");
    }

    public static String normalizeDashboardId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean validSuffixIdentifier(String value) {
        return value != null && value.matches("[a-z][a-z0-9-]{0,62}[a-z0-9]");
    }

    public static boolean htmlMode(DashboardSpec spec) {
        return spec != null && DashboardSpec.MODE_HTML.equals(spec.getMode());
    }

    public static boolean lowCodeMode(DashboardSpec spec) {
        return spec != null && DashboardSpec.MODE_LOW_CODE.equals(spec.getMode());
    }

    public static String manifestPath(DashboardSpec spec) {
        return htmlMode(spec) ? spec.getDashboardId() + ".manifest.json" : "_dashboard.json";
    }

    public static String configType(DashboardSpec spec) {
        return htmlMode(spec) ? "html-page" : spec.getDashboardId();
    }

    public static String htmlPath(DashboardSpec spec) {
        return spec.getDashboardId() + ".html";
    }

    public static String configIndex(DashboardSpec spec) {
        return spec.getDashboardId();
    }

    public static String contentPath(DashboardSpec spec) {
        return htmlMode(spec) ? htmlPath(spec) : "index.json";
    }

    public static String sortedFileDigest(Map<String, String> files) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(files.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        return digest(entries.stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce("", (left, right) -> left + right + "\n"));
    }
}
