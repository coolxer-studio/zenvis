package com.coolxer.dao.mysql.entity;

import com.coolxer.commons.enums.PluginStatusType;
import jakarta.persistence.Column;
import org.hibernate.annotations.Check;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PluginSchemaContractTest {

    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile("'([^']+)'");

    @Test
    void statusColumnUsesVarcharAndConstrainsEveryCurrentPluginStatus() throws Exception {
        Field statusField = Plugin.class.getDeclaredField("status");
        Column column = statusField.getAnnotation(Column.class);
        Check check = Plugin.class.getAnnotation(Check.class);

        assertThat(column.columnDefinition()).isEqualTo("VARCHAR(32)");
        assertThat(column.nullable()).isFalse();
        assertThat(check.name()).isEqualTo("t_sys_plugin_status_chk");
        assertThat(quotedValues(check.constraints()))
                .containsExactly(Arrays.stream(PluginStatusType.values())
                        .map(Enum::name)
                        .toArray(String[]::new));
        assertThat(new Plugin().getStatus()).isEqualTo(PluginStatusType.UN_INSTALL);
    }

    private static String[] quotedValues(String checkConstraint) {
        Matcher matcher = QUOTED_VALUE_PATTERN.matcher(checkConstraint);
        return matcher.results()
                .map(result -> result.group(1))
                .toArray(String[]::new);
    }
}
