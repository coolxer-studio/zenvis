package com.coolxer.service.system;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.model.system.dto.PushTaskValidationRequest;
import com.coolxer.model.system.validation.PushTaskDiagnosticVo;
import com.coolxer.model.system.validation.PushTaskValidationVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PushTaskConfigValidationServiceTest {

    private static final String DATA_SERVICE_URL = "http://vectum.test";
    private static final String CONFIG = """
            sources:
              src:
                type: demo_logs
            sinks:
              out:
                type: console
                inputs: [src]
            """;

    private PushTaskConfigValidationService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        CustomWebConfig config = new CustomWebConfig();
        ReflectionTestUtils.setField(config, "dataServiceUrl", DATA_SERVICE_URL);
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new PushTaskConfigValidationService(
                new PushTaskConfigRules(), config, restTemplate);
    }

    @Test
    void passesWhenStaticAndNativeValidationAgreeOnDigestAndTests() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/validate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"status":0,"data":{"passed":true,"blocked":false,"format":"yaml",
                         "candidate_digest":"%s","vector_version":"vector 0.55.0",
                         "diagnostics":[],
                         "tests":{"present":true,"total":2,"passed":2,"failed":0,
                                  "cases":[{"name":"valid","status":"passed"}]},
                         "normalized_config":"normalized"}}
                        """.formatted(digest(CONFIG)), MediaType.APPLICATION_JSON));

        PushTaskValidationVo result = service.validate(request(CONFIG, ""));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.isBlocked()).isFalse();
        assertThat(result.getCandidateDigest()).isEqualTo(digest(CONFIG));
        assertThat(result.getValidationDigest()).hasSize(64);
        assertThat(result.getTests().getTotal()).isEqualTo(2);
        assertThat(result.getTests().getPassed()).isEqualTo(2);
        assertThat(result.getNormalizedConfig()).isEqualTo("normalized");
        server.verify();
    }

    @Test
    void mergesStaticAndNativeDiagnostics() {
        String invalidConfig = """
                sources:
                  src:
                    type: demo_logs
                sinks:
                  - type: console
                    inputs: [src]
                """;
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/validate"))
                .andRespond(withSuccess("""
                        {"status":0,"data":{"passed":false,"blocked":false,"format":"yaml",
                         "candidate_digest":"%s","vector_version":"vector 0.55.0",
                         "diagnostics":[{"code":"VECTOR_SCHEMA_ARRAY_AS_MAP",
                           "severity":"ERROR","source":"VECTOR","path":"$sinks",
                           "message":"expected a map","repair_hint":"use map"}],
                         "tests":{"present":false,"total":0,"passed":0,"failed":0,"cases":[]}}}
                        """.formatted(digest(invalidConfig)), MediaType.APPLICATION_JSON));

        PushTaskValidationVo result = service.validate(request(invalidConfig, ""));

        assertThat(result.isPassed()).isFalse();
        assertThat(codes(result)).contains(
                "PUSH_COMPONENT_MAP_REQUIRED", "VECTOR_SCHEMA_ARRAY_AS_MAP");
        server.verify();
    }

    @Test
    void missingNativeCandidateDigestCannotBeTrusted() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/validate"))
                .andRespond(withSuccess("""
                        {"status":0,"data":{"passed":true,"blocked":false,"format":"yaml",
                         "vector_version":"vector 0.55.0","diagnostics":[],
                         "tests":{"present":false,"total":0,"passed":0,"failed":0,"cases":[]}}}
                        """, MediaType.APPLICATION_JSON));

        PushTaskValidationVo result = service.validate(request(CONFIG, ""));

        assertThat(result.isPassed()).isFalse();
        assertThat(codes(result)).contains("PUSH_VALIDATION_DIGEST_MISMATCH");
        assertThat(result.getDiagnostics())
                .extracting(PushTaskDiagnosticVo::getMessage)
                .anyMatch(message -> message.contains("未返回候选摘要"));
        server.verify();
    }

    @Test
    void mismatchedNativeCandidateDigestCannotBeTrusted() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/validate"))
                .andRespond(withSuccess("""
                        {"status":0,"data":{"passed":true,"blocked":false,"format":"yaml",
                         "candidate_digest":"different-digest","vector_version":"vector 0.55.0",
                         "diagnostics":[],"tests":{"present":false,"total":0,
                         "passed":0,"failed":0,"cases":[]}}}
                        """, MediaType.APPLICATION_JSON));

        PushTaskValidationVo result = service.validate(request(CONFIG, ""));

        assertThat(result.isPassed()).isFalse();
        assertThat(codes(result)).contains("PUSH_VALIDATION_DIGEST_MISMATCH");
        server.verify();
    }

    @Test
    void vectumFailureFailsClosed() {
        server.expect(once(), requestTo(DATA_SERVICE_URL + "/vectum/api/v1/task/validate"))
                .andRespond(withServerError());

        PushTaskValidationVo result = service.validate(request(CONFIG, ""));

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isBlocked()).isTrue();
        assertThat(codes(result)).contains("PUSH_VALIDATION_UNAVAILABLE");
        assertThat(result.getVectorVersion()).isEqualTo("unavailable");
        server.verify();
    }

    @Test
    void ambiguousFormatDoesNotCallVectum() {
        PushTaskValidationRequest request = request(
                "# comment\nnot a vector config", "");
        request.setFormat("");

        PushTaskValidationVo result = service.validate(request);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isBlocked()).isFalse();
        assertThat(codes(result)).contains("PUSH_CONFIG_FORMAT_AMBIGUOUS");
        assertThat(result.getValidationDigest()).hasSize(64);
        server.verify();
    }

    private PushTaskValidationRequest request(String config, String metaContent) {
        PushTaskValidationRequest request = new PushTaskValidationRequest();
        request.setConfig(config);
        request.setFormat("yaml");
        request.setMetaContent(metaContent);
        return request;
    }

    private List<String> codes(PushTaskValidationVo result) {
        return result.getDiagnostics().stream()
                .map(PushTaskDiagnosticVo::getCode)
                .toList();
    }

    private String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
