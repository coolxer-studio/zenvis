package com.coolxer.service.dih;

import org.springframework.util.StringUtils;

/**
 * Conservative tokenizer-independent estimate for OpenAI-compatible chat input.
 *
 * <p>CJK and other non-ASCII code points are counted as one token each. ASCII
 * content is estimated at four characters per token. The estimate deliberately
 * leaves model-specific variance to the configured safety margin.</p>
 */
final class DihTokenEstimator {

    private static final String TRUNCATION_MARKER =
            "\n\n[内容已按模型上下文预算截断，省略部分不得推断]\n\n";

    int estimate(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int ascii = 0;
        int nonAscii = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (codePoint <= 0x7f) {
                ascii++;
            } else {
                nonAscii++;
            }
            offset += Character.charCount(codePoint);
        }
        return nonAscii + Math.max(1, (ascii + 3) / 4);
    }

    String truncate(String text, int maxTokens) {
        if (!StringUtils.hasText(text) || maxTokens <= 0) {
            return "";
        }
        if (estimate(text) <= maxTokens) {
            return text;
        }

        int markerTokens = estimate(TRUNCATION_MARKER);
        int contentBudget = Math.max(maxTokens - markerTokens, 1);
        int estimated = Math.max(estimate(text), 1);
        int targetChars = Math.max(1,
                (int) Math.floor((double) text.length() * contentBudget / estimated));
        targetChars = Math.min(targetChars, text.length());

        String truncated = headAndTail(text, targetChars);
        while (targetChars > 1 && estimate(truncated) + markerTokens > maxTokens) {
            targetChars = Math.max(1, targetChars * 9 / 10);
            truncated = headAndTail(text, targetChars);
        }
        int split = Math.max(1, truncated.length() * 4 / 5);
        return truncated.substring(0, split)
                + TRUNCATION_MARKER
                + truncated.substring(split);
    }

    private String headAndTail(String text, int targetChars) {
        if (targetChars >= text.length()) {
            return text;
        }
        int headChars = Math.max(1, targetChars * 4 / 5);
        int tailChars = Math.max(0, targetChars - headChars);
        return text.substring(0, Math.min(headChars, text.length()))
                + text.substring(Math.max(text.length() - tailChars, headChars));
    }
}
