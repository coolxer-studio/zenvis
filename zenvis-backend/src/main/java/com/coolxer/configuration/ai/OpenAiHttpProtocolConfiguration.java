package com.coolxer.configuration.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Forces OpenAI-compatible HTTP calls to use HTTP/1.1.
 *
 * <p>The JDK HTTP client prefers HTTP/2 and attempts an h2c upgrade for clear-text HTTP URLs.
 * Some OpenAI-compatible Uvicorn gateways do not consume the request body correctly when a
 * POST request carries that upgrade. Spring AI obtains its WebClient and RestClient builders
 * from Boot, so customizing both builders keeps streaming and non-streaming calls consistent.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.ai.openai.force-http1",
        havingValue = "true",
        matchIfMissing = true
)
public class OpenAiHttpProtocolConfiguration {

    private static final String OPEN_AI_HTTP_1_CLIENT = "openAiHttp1Client";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    @Bean(OPEN_AI_HTTP_1_CLIENT)
    HttpClient openAiHttp1Client() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Bean
    WebClientCustomizer openAiHttp1WebClientCustomizer(
            @Qualifier(OPEN_AI_HTTP_1_CLIENT) HttpClient httpClient
    ) {
        return builder -> builder.clientConnector(new JdkClientHttpConnector(httpClient));
    }

    @Bean
    RestClientCustomizer openAiHttp1RestClientCustomizer(
            @Qualifier(OPEN_AI_HTTP_1_CLIENT) HttpClient httpClient
    ) {
        return builder -> builder.requestFactory(new JdkClientHttpRequestFactory(httpClient));
    }
}
