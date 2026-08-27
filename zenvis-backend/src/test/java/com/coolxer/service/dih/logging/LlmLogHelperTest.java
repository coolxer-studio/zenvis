package com.coolxer.service.dih.logging;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmLogHelperTest {

    @Test
    void includesProviderStatusRequestAndResponseBodyForWebClientErrors() {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getURI()).thenReturn(URI.create("http://model-service/v1/chat/completions"));
        byte[] responseBody = """
                {
                  "error": {
                    "message": "unsupported parameter: stream_options",
                    "api_key": "must-not-appear"
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);
        WebClientResponseException exception = WebClientResponseException.create(
                400,
                "Bad Request",
                HttpHeaders.EMPTY,
                responseBody,
                StandardCharsets.UTF_8,
                request
        );

        String details = LlmLogHelper.toLogText(LlmLogHelper.httpErrorDetails(exception));

        assertThat(details)
                .contains("\"status_code\":400")
                .contains("\"request_method\":\"POST\"")
                .contains("\"request_url\":\"http://model-service/v1/chat/completions\"")
                .contains("unsupported parameter: stream_options")
                .contains("\"api_key\":\"******\"")
                .doesNotContain("must-not-appear");
    }

    @Test
    void findsWebClientErrorsWrappedByAnotherException() {
        WebClientResponseException responseException = WebClientResponseException.create(
                429,
                "Too Many Requests",
                HttpHeaders.EMPTY,
                "rate limited".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        Map<String, Object> details =
                LlmLogHelper.httpErrorDetails(new IllegalStateException("wrapped", responseException));

        assertThat(details)
                .containsEntry("status_code", 429)
                .containsEntry("status_text", "Too Many Requests");
        assertThat(LlmLogHelper.toLogText(details)).contains("rate limited");
    }

    @Test
    void returnsEmptyDetailsForNonHttpErrors() {
        assertThat(LlmLogHelper.httpErrorDetails(new IllegalArgumentException("bad request"))).isEmpty();
    }
}
