package com.coolxer.service.impl;

import com.coolxer.config.KerberosProperties;
import com.coolxer.service.MonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VectorServiceImplTest {

    @TempDir
    Path tempDir;

    private Path workspace;
    private VectorServiceImpl service;

    @BeforeEach
    void setUp() {
        workspace = tempDir.resolve("workspace");
        KerberosProperties kerberos = new KerberosProperties();
        // 默认关闭 Kerberos，不影响现有测试
        kerberos.setEnabled(false);
        service = new VectorServiceImpl(
                mock(MonitorService.class),
                tempDir.resolve("vector").toString(),
                workspace.toString(),
                kerberos);
    }

    @Test
    void writesConfigWithExplicitYamlTomlAndJsonExtensions() {
        assertThat(service.createProcess("10", "sources: {}", null, "yaml")).isNull();
        assertThat(workspace.resolve("10/push.yaml")).isRegularFile();

        assertThat(service.createProcess("10", "[sources.demo]", null, "toml")).isNull();
        assertThat(workspace.resolve("10/push.yaml")).doesNotExist();
        assertThat(workspace.resolve("10/push.toml")).isRegularFile();

        assertThat(service.createProcess("10", "{\"sources\":{}}", null, "json")).isNull();
        assertThat(workspace.resolve("10/push.toml")).doesNotExist();
        assertThat(workspace.resolve("10/push.json")).isRegularFile();
    }

    @Test
    void ymlAliasWritesYamlAndRemovesLegacyExtension() throws Exception {
        Path legacy = workspace.resolve("10/push.yml");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "legacy");

        assertThat(service.createProcess("10", "sources: {}", null, "yml")).isNull();

        assertThat(legacy).doesNotExist();
        assertThat(workspace.resolve("10/push.yaml")).isRegularFile();
        assertThat(Files.readString(workspace.resolve("10/push.yaml"))).isEqualTo("sources: {}");
    }

    @Test
    void invalidFormatFailsWithoutDestroyingExistingConfig() throws Exception {
        assertThat(service.createProcess("10", "sources: {}", null, "yaml")).isNull();

        assertThat(service.createProcess("10", "sources: {changed: true}", null, "ini"))
                .contains("不支持的配置格式");
        assertThat(Files.readString(workspace.resolve("10/push.yaml")))
                .isEqualTo("sources: {}");
    }

    @Test
    void updateProcessSwitchesFormatAndCleansPreviousFile() {
        assertThat(service.createProcess("10", "sources: {}", null, "yaml")).isNull();

        assertThat(service.updateProcess("10", "[sources.demo]", null, "toml")).isNull();

        assertThat(workspace.resolve("10/push.yaml")).doesNotExist();
        assertThat(workspace.resolve("10/push.toml")).isRegularFile();
    }

    @Test
    void exposesInterpolationAndKerberosEnvironment() throws Exception {
        Path krb5Conf = tempDir.resolve("krb5.conf");
        Path keytab = tempDir.resolve("client.keytab");
        Files.writeString(krb5Conf, "");
        Files.writeString(keytab, "");

        KerberosProperties kerberos = new KerberosProperties();
        kerberos.setEnabled(true);
        kerberos.setKrb5ConfigPath(krb5Conf.toString());
        kerberos.setKeytabPath(keytab.toString());
        kerberos.setCcachePath("/tmp/krb5cc_test");
        VectorServiceImpl kerberosService = new VectorServiceImpl(
                mock(MonitorService.class),
                tempDir.resolve("vector").toString(),
                workspace.toString(),
                kerberos);

        Map<String, String> env = kerberosService.buildVectorProcessEnvironment();

        assertThat(env)
                .containsEntry("VECTOR_DANGEROUSLY_ALLOW_ENV_VAR_INTERPOLATION", "true")
                .containsEntry("KRB5_CONFIG", krb5Conf.toString())
                .containsEntry("KRB5_KTNAME", keytab.toString())
                .containsEntry("KRB5CCNAME", "/tmp/krb5cc_test");
    }

    @Test
    void passesKerberosAndInterpolationEnvironmentToVectorProcess() throws Exception {
        Path vectorHome = tempDir.resolve("vector-runtime");
        Path executable = vectorHome.resolve("bin").resolve("vector");
        Files.createDirectories(executable.getParent());
        Path envDump = tempDir.resolve("vector-env.txt");
        Files.writeString(executable, "#!/bin/sh\nenv > " + envDump + "\n");
        assertThat(executable.toFile().setExecutable(true)).isTrue();

        Path krb5Conf = tempDir.resolve("krb5.conf");
        Path keytab = tempDir.resolve("client.keytab");
        Files.writeString(krb5Conf, "");
        Files.writeString(keytab, "");

        KerberosProperties kerberos = new KerberosProperties();
        kerberos.setEnabled(true);
        kerberos.setKrb5ConfigPath(krb5Conf.toString());
        kerberos.setKeytabPath(keytab.toString());
        kerberos.setCcachePath("/tmp/krb5cc_test");
        VectorServiceImpl kerberosService = new VectorServiceImpl(
                mock(MonitorService.class),
                vectorHome.toString(),
                workspace.toString(),
                kerberos);

        kerberosService.init();
        assertThat(kerberosService.createProcess("42", "sources: {}", null, "yaml")).isNull();
        assertThat(kerberosService.startProcess("42")).isPositive();

        for (int attempt = 0; attempt < 50 && !Files.exists(envDump); attempt++) {
            Thread.sleep(100);
        }
        assertThat(envDump).isRegularFile();
        assertThat(Files.readString(envDump))
                .contains("VECTOR_DANGEROUSLY_ALLOW_ENV_VAR_INTERPOLATION=true")
                .contains("KRB5_CONFIG=" + krb5Conf)
                .contains("KRB5_KTNAME=" + keytab)
                .contains("KRB5CCNAME=/tmp/krb5cc_test");
    }
}
