package com.coolxer.configuration.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiHttpProtocolConfigurationTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void registersHttp1CustomizersByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(OpenAiHttpProtocolConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(WebClientCustomizer.class);
                    assertThat(context).hasSingleBean(RestClientCustomizer.class);
                });
    }

    @Test
    void allowsHttp1CustomizationToBeDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(OpenAiHttpProtocolConfiguration.class)
                .withPropertyValues("app.ai.openai.force-http1=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(WebClientCustomizer.class);
                    assertThat(context).doesNotHaveBean(RestClientCustomizer.class);
                });
    }

    @Test
    void webClientSendsBodyWithoutH2cUpgrade() throws IOException {
        AtomicReference<CapturedRequest> captured = startCaptureServer();
        OpenAiHttpProtocolConfiguration configuration = new OpenAiHttpProtocolConfiguration();
        var httpClient = configuration.openAiHttp1Client();
        WebClient.Builder builder = WebClient.builder();
        configuration.openAiHttp1WebClientCustomizer(httpClient).customize(builder);

        String response = builder.build()
                .post()
                .uri(serverUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"test\"}")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertThat(response).isEqualTo("{}");
        assertHttp1Request(captured.get());
    }

    @Test
    void restClientSendsBodyWithoutH2cUpgrade() throws IOException {
        AtomicReference<CapturedRequest> captured = startCaptureServer();
        OpenAiHttpProtocolConfiguration configuration = new OpenAiHttpProtocolConfiguration();
        var httpClient = configuration.openAiHttp1Client();
        RestClient.Builder builder = RestClient.builder();
        configuration.openAiHttp1RestClientCustomizer(httpClient).customize(builder);

        String response = builder.build()
                .post()
                .uri(serverUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"model\":\"test\"}")
                .retrieve()
                .body(String.class);

        assertThat(response).isEqualTo("{}");
        assertHttp1Request(captured.get());
    }

    private AtomicReference<CapturedRequest> startCaptureServer() throws IOException {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", exchange -> capture(exchange, captured));
        server.start();
        return captured;
    }

    private void capture(HttpExchange exchange, AtomicReference<CapturedRequest> captured) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        captured.set(new CapturedRequest(
                exchange.getProtocol(),
                exchange.getRequestHeaders().getFirst("Upgrade"),
                exchange.getRequestHeaders().getFirst("HTTP2-Settings"),
                new String(requestBody, StandardCharsets.UTF_8)
        ));

        byte[] responseBody = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(200, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private String serverUrl() {
        return "http://localhost:" + server.getAddress().getPort() + "/v1/chat/completions";
    }

    private void assertHttp1Request(CapturedRequest request) {
        assertThat(request).isNotNull();
        assertThat(request.protocol()).isEqualTo("HTTP/1.1");
        assertThat(request.upgrade()).isNull();
        assertThat(request.http2Settings()).isNull();
        assertThat(request.body()).isEqualTo("{\"model\":\"test\"}");
    }

    private record CapturedRequest(String protocol, String upgrade, String http2Settings, String body) {
    }
}
