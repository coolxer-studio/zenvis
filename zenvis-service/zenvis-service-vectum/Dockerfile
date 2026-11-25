ARG BASE_IMAGE_ARCH=none
FROM crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/eclipse-temurin:17-jdk-${BASE_IMAGE_ARCH}

WORKDIR /app

# Install Kerberos client libraries so Vector's embedded librdkafka
# can authenticate against Kerberos Kafka clusters via GSSAPI.
# krb5-user provides kinit/klist for librdkafka's Kerberos flow;
# libsasl2-modules-gssapi-mit is required when Vector is built with the
# dynamic gssapi feature (the vendored build embeds libsasl2).
RUN export DEBIAN_FRONTEND=noninteractive \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        krb5-user libsasl2-modules-gssapi-mit ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create directories for external Kerberos files to be mounted in.
# Users mount their krb5.conf and service keytab into these locations.
RUN mkdir -p /etc/krb5 /workspace/kerberos

COPY target/application.jar ./application.jar
COPY src/main/resources/application-prod.properties ./application.properties
COPY vector/vector /vector

# Fail the build early if the Vector binary is missing or cannot start.
RUN /vector/bin/vector --version

EXPOSE 11002

ENTRYPOINT ["java", "-jar", "application.jar", "--spring.config.location=file:./application.properties"]
