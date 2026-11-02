package com.coolxer.service.dih.dataapp;

import com.coolxer.configuration.JacksonConfig;
import com.coolxer.model.dih.dataapp.DataAppSpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DataAppSupport {

    private DataAppSupport() {
    }

    public static String canonicalJson(Object value) {
        return JacksonConfig.OBJECT_MAPPER.convertValue(value, Object.class) == null
                ? "null"
                : com.coolxer.utils.JacksonUtil.toJson(value);
    }

    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算数据应用摘要", e);
        }
    }

    public static String digestObject(Object value) {
        return digest(canonicalJson(value));
    }

    public static boolean validAppId(String value) {
        return value != null && value.matches("[a-z][a-z0-9-]{1,62}[a-z0-9]");
    }

    public static boolean validPageId(String value) {
        return value != null && value.matches("[a-z][a-z0-9-]{0,62}[a-z0-9]");
    }

    public static String normalizeAppId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizePageId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String fileNameForPage(String appId, String pageId, boolean singlePage) {
        return singlePage ? "index.json" : "pages/" + pageId + ".json";
    }

    public static String sortedFileDigest(Map<String, String> files) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(files.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        return digest(entries.stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce("", (left, right) -> left + right + "\n"));
    }

    public static boolean isSinglePage(DataAppSpec spec) {
        return spec != null && spec.getPages() != null && spec.getPages().size() == 1;
    }
}
