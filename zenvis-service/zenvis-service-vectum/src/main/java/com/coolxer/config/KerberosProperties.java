package com.coolxer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kerberos 认证配置属性。
 * <p>
 * 用于向 Vector 进程注入 Kerberos 所需的 KRB5_* 环境变量，
 * 使得 Vector 内置的 librdkafka 能够通过 GSSAPI 机制认证 Kafka 集群。
 * 任务级别的 librdkafka_options（security.protocol、sasl.mechanism、
 * sasl.jaas.config 等）由用户在 Vector YAML 配置中直接提供，
 * 本配置类只负责进程级环境变量。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "vectum.kerberos")
public class KerberosProperties {

    /**
     * 是否启用 Kerberos 环境变量注入。
     * 默认 false，启用前需确保容器内已安装 krb5 用户态库并正确挂载 krb5.conf / keytab 文件。
     */
    private boolean enabled = false;

    /**
     * Kerberos 配置文件路径，对应进程环境变量 KRB5_CONFIG。
     * 典型位置 /etc/krb5.conf。
     */
    private String krb5ConfigPath = "/etc/krb5.conf";

    /**
     * Service principal keytab 文件路径，对应进程环境变量 KRB5_KTNAME。
     * 典型位置 /etc/krb5/vectum.keytab。
     * 可选：当 librdkafka_options.sasl.jaas.config 中已内联 keyTab 路径时可省略。
     */
    private String keytabPath = "/etc/krb5/vectum.keytab";

    /**
     * Service principal，如 vectum@YOUR-REALM.COM。
     * 用于在 Vector YAML 的 librdkafka_options.sasl.jaas.config 中引用。
     * 此处仅作为占位符，实际注入进程的是环境变量而非 principal 字段。
     */
    private String principal = "vectum@YOUR-REALM.COM";

    /**
     * Kafka Broker 的 Kerberos service name，对应 librdkafka_options.sasl.kerberos.service.name。
     * 典型值为 "kafka"。
     * 此处仅作为系统默认文档值；实际 Kafka source/sink 中的 librdkafka_options 由用户配置。
     */
    private String serviceName = "kafka";

    /**
     * Kerberos credential cache 路径，对应进程环境变量 KRB5CCNAME。
     * 可选：默认 /tmp/krb5cc_vectum。
     */
    private String ccachePath = "/tmp/krb5cc_vectum";

    /**
     * 判断 Kerberos 环境变量是否可安全注入 Vector 进程。
     * 只有当 enabled=true 且 krb5ConfigPath 在容器中实际存在时才返回 true。
     * 避免向 librdkafka 传递无效路径导致其启动时崩溃。
     *
     * @return true 表示可以向子进程注入 KRB5_* 环境变量
     */
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        return krb5ConfigPath != null
                && Files.exists(Path.of(krb5ConfigPath));
    }

    /**
     * 构造要注入 Vector 进程的环境变量 Map。
     * 仅填充对应文件路径实际存在的 KRB5_* 变量，避免无效值传递给 librdkafka。
     *
     * @return 环境变量 Map，可能为空（当 isAvailable() 为 false 或文件缺失时）
     */
    public Map<String, String> buildProcessEnvironment() {
        Map<String, String> env = new LinkedHashMap<>();
        if (!enabled) {
            return env;
        }
        putIfFileExists(env, "KRB5_CONFIG", krb5ConfigPath);
        putIfFileExists(env, "KRB5_KTNAME", keytabPath);
        if (ccachePath != null && !ccachePath.isBlank()) {
            env.put("KRB5CCNAME", ccachePath);
        }
        return env;
    }

    private static void putIfFileExists(Map<String, String> env, String key, String value) {
        if (value != null && !value.isBlank() && Files.exists(Path.of(value))) {
            env.put(key, value);
        }
    }
}
