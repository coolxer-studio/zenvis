package com.coolxer.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KerberosPropertiesTest {

    @TempDir
    Path tempDir;

    @Test
    void disabledKerberosInjectsNothing() {
        KerberosProperties properties = new KerberosProperties();

        assertThat(properties.isAvailable()).isFalse();
        assertThat(properties.buildProcessEnvironment()).isEmpty();
    }

    @Test
    void injectsOnlyExistingFiles() throws Exception {
        Path krb5Conf = tempDir.resolve("krb5.conf");
        Files.writeString(krb5Conf, "");

        KerberosProperties properties = new KerberosProperties();
        properties.setEnabled(true);
        properties.setKrb5ConfigPath(krb5Conf.toString());
        properties.setKeytabPath(tempDir.resolve("missing.keytab").toString());
        properties.setCcachePath("/tmp/krb5cc_test");

        Map<String, String> env = properties.buildProcessEnvironment();

        assertThat(properties.isAvailable()).isTrue();
        assertThat(properties.isKeytabAvailable()).isFalse();
        assertThat(env)
                .containsEntry("KRB5_CONFIG", krb5Conf.toString())
                .containsEntry("KRB5CCNAME", "/tmp/krb5cc_test")
                .doesNotContainKey("KRB5_KTNAME");
    }

    @Test
    void missingKrb5ConfIsNotAvailable() {
        KerberosProperties properties = new KerberosProperties();
        properties.setEnabled(true);
        properties.setKrb5ConfigPath(tempDir.resolve("missing.conf").toString());

        assertThat(properties.isAvailable()).isFalse();
    }
}
