ARG BASE_IMAGE_ARCH=none
FROM crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/eclipse-temurin:17-jdk-${BASE_IMAGE_ARCH}

WORKDIR /app

COPY target/application.jar ./application.jar
COPY src/main/resources/application-prod.properties ./application.properties
COPY vector/vector /vector

EXPOSE 11002

ENTRYPOINT ["java", "-jar", "application.jar", "--spring.config.location=file:./application.properties"]
