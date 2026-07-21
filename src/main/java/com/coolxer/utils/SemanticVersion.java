package com.coolxer.utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 严格的 Semantic Versioning 2.0.0 解析与比较器。
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

    private static final String NUMERIC = "0|[1-9][0-9]*";
    private static final String IDENTIFIER = "[0-9A-Za-z-]+";
    private static final Pattern SEMVER = Pattern.compile(
            "^(" + NUMERIC + ")\\.(" + NUMERIC + ")\\.(" + NUMERIC + ")"
                    + "(?:-(" + IDENTIFIER + "(?:\\." + IDENTIFIER + ")*))?"
                    + "(?:\\+(" + IDENTIFIER + "(?:\\." + IDENTIFIER + ")*))?$"
    );

    private final String value;
    private final BigInteger major;
    private final BigInteger minor;
    private final BigInteger patch;
    private final List<String> preRelease;

    private SemanticVersion(String value,
                            BigInteger major,
                            BigInteger minor,
                            BigInteger patch,
                            List<String> preRelease) {
        this.value = value;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
    }

    public static SemanticVersion parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("版本不能为空");
        }
        Matcher matcher = SEMVER.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("版本不是合法的 SemVer 2.0.0: " + value);
        }
        List<String> preRelease = splitIdentifiers(matcher.group(4));
        for (String identifier : preRelease) {
            if (isNumeric(identifier) && identifier.length() > 1 && identifier.startsWith("0")) {
                throw new IllegalArgumentException("SemVer 预发布数字标识不能包含前导零: " + value);
            }
        }
        return new SemanticVersion(
                value.trim(),
                new BigInteger(matcher.group(1)),
                new BigInteger(matcher.group(2)),
                new BigInteger(matcher.group(3)),
                List.copyOf(preRelease)
        );
    }

    private static List<String> splitIdentifiers(String identifiers) {
        if (identifiers == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String identifier : identifiers.split("\\.")) {
            if (identifier.isEmpty()) {
                throw new IllegalArgumentException("SemVer 标识不能为空");
            }
            values.add(identifier);
        }
        return values;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        Objects.requireNonNull(other, "other");
        int compared = major.compareTo(other.major);
        if (compared == 0) {
            compared = minor.compareTo(other.minor);
        }
        if (compared == 0) {
            compared = patch.compareTo(other.patch);
        }
        if (compared != 0) {
            return compared;
        }
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            if (preRelease.isEmpty() && other.preRelease.isEmpty()) {
                return 0;
            }
            return preRelease.isEmpty() ? 1 : -1;
        }
        int length = Math.max(preRelease.size(), other.preRelease.size());
        for (int index = 0; index < length; index++) {
            if (index >= preRelease.size()) {
                return -1;
            }
            if (index >= other.preRelease.size()) {
                return 1;
            }
            String left = preRelease.get(index);
            String right = other.preRelease.get(index);
            boolean leftNumeric = isNumeric(left);
            boolean rightNumeric = isNumeric(right);
            if (leftNumeric && rightNumeric) {
                compared = new BigInteger(left).compareTo(new BigInteger(right));
            } else if (leftNumeric != rightNumeric) {
                compared = leftNumeric ? -1 : 1;
            } else {
                compared = left.compareTo(right);
            }
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static boolean isNumeric(String value) {
        return value.chars().allMatch(Character::isDigit);
    }

    @Override
    public String toString() {
        return value;
    }
}
