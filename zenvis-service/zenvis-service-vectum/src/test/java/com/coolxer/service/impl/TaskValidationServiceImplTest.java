package com.coolxer.service.impl;

import com.coolxer.model.validation.ConfigValidationResult;
import com.coolxer.model.validation.ValidationDiagnostic;
import com.coolxer.model.validation.ValidationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TaskValidationServiceImplTest {

    private static final String VALID_CONFIG = """
            sources:
              demo:
                type: demo_logs
            sinks:
              out:
                type: console
                inputs: [demo]
            tests:
              - name: valid
              - name: invalid
            """;

    @TempDir
    Path tempDir;

    @Test
    void validatesYamlAndReturnsVectorTestSummary() throws Exception {
        TaskValidationServiceImpl service = service(5, fakeVector());

        ConfigValidationResult result = service.validate(request(VALID_CONFIG, false));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.isBlocked()).isFalse();
        assertThat(result.getFormat()).isEqualTo("yaml");
        assertThat(result.getVectorVersion()).isEqualTo("vector 0.55.0");
        assertThat(result.getCandidateDigest()).hasSize(64);
        assertThat(result.getTests().isPresent()).isTrue();
        assertThat(result.getTests().getTotal()).isEqualTo(2);
        assertThat(result.getTests().getPassed()).isEqualTo(2);
        assertThat(result.getTests().getFailed()).isZero();
    }

    @Test
    void mapsVectorSchemaErrorToStructuredDiagnostic() throws Exception {
        TaskValidationServiceImpl service = service(5, fakeVector());

        ConfigValidationResult result = service.validate(request("""
                sinks:
                  schema-array: true
                """, false));

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isBlocked()).isFalse();
        assertThat(codes(result)).contains("VECTOR_SCHEMA_ARRAY_AS_MAP");
    }

    @Test
    void mapsVrlCompileFailureToStructuredDiagnostic() throws Exception {
        TaskValidationServiceImpl service = service(5, fakeVector());

        ConfigValidationResult result = service.validate(request("""
                transforms:
                  broken:
                    vrl-broken: true
                """, false));

        assertThat(result.isPassed()).isFalse();
        assertThat(codes(result)).contains("VECTOR_VRL_ERROR");
    }

    @Test
    void failedVectorTestsFailClosed() throws Exception {
        TaskValidationServiceImpl service = service(5, fakeVector());

        ConfigValidationResult result = service.validate(request("""
                sources:
                  demo:
                    type: demo_logs
                tests:
                  - name: valid
                  - name: invalid
                test-failed: true
                """, true));

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getTests().getFailed()).isEqualTo(1);
        assertThat(result.getTests().getPassed()).isEqualTo(1);
        assertThat(codes(result)).contains("VECTOR_TEST_FAILED");
    }

    @Test
    void requiredTestsMustBePresent() throws Exception {
        TaskValidationServiceImpl service = service(5, fakeVector());

        ConfigValidationResult result = service.validate(request("""
                sources:
                  demo:
                    type: demo_logs
                """, true));

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getTests().isPresent()).isFalse();
        assertThat(codes(result)).contains("VECTOR_TESTS_REQUIRED");
    }

    @Test
    void injectsNonSensitivePlaceholdersForMissingEnvironment() throws Exception {
        TaskValidationServiceImpl service = service(5, fakeVector());

        ConfigValidationResult result = service.validate(request("""
                sources:
                  demo:
                    type: demo_logs
                    endpoint: ${VALIDATION_ENDPOINT}
                """, false));

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getDiagnostics()).isEmpty();
    }

    @Test
    void timeoutIsBlockedAndTemporaryDirectoryIsCleaned() throws Exception {
        Path fakeVector = fakeVector();
        TaskValidationServiceImpl service = service(1, fakeVector);
        Set<String> before = validationDirectories();

        long started = System.nanoTime();
        ConfigValidationResult result = service.validate(request("""
                sources:
                  slow:
                    force-timeout: true
                """, false));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isBlocked()).isTrue();
        assertThat(codes(result)).contains("VECTOR_VALIDATE_TIMEOUT");
        assertThat(elapsed).isLessThan(Duration.ofSeconds(6));
        assertThat(validationDirectories()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void missingVectorBinaryIsBlocked() {
        TaskValidationServiceImpl service = new TaskValidationServiceImpl(
                tempDir.resolve("missing-vector-home").toString(), 5);

        ConfigValidationResult result = service.validate(request(VALID_CONFIG, false));

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isBlocked()).isTrue();
        assertThat(codes(result)).contains("VECTOR_VALIDATION_UNAVAILABLE");
    }

    private TaskValidationServiceImpl service(int timeoutSeconds, Path vector) {
        return new TaskValidationServiceImpl(vector.getParent().getParent().toString(),
                timeoutSeconds);
    }

    private ValidationRequest request(String config, boolean testsRequired) {
        ValidationRequest request = new ValidationRequest();
        request.setConfig(config);
        request.setFormat("yaml");
        request.setTestsRequired(testsRequired);
        return request;
    }

    private Set<String> codes(ConfigValidationResult result) {
        return result.getDiagnostics().stream()
                .map(ValidationDiagnostic::getCode)
                .collect(Collectors.toSet());
    }

    private Path fakeVector() throws IOException {
        Path home = tempDir.resolve("vector-home");
        Path bin = home.resolve("bin");
        Files.createDirectories(bin);
        Path vector = bin.resolve("vector");
        Files.writeString(vector, """
                #!/bin/sh
                if [ "$1" = "--version" ]; then
                  echo "vector 0.55.0"
                  exit 0
                fi
                config=""
                for arg in "$@"; do config="$arg"; done
                if [ "$1" = "validate" ]; then
                  if grep -q "force-timeout" "$config"; then sleep 5; exit 0; fi
                  if grep -q "schema-array" "$config"; then
                    echo "invalid type: sequence, expected a map"
                    exit 1
                  fi
                  if grep -q "vrl-broken" "$config"; then
                    echo "error[E203]: failed to compile VRL"
                    exit 1
                  fi
                  if grep -q "VALIDATION_ENDPOINT" "$config" && [ -z "$VALIDATION_ENDPOINT" ]; then
                    echo "missing environment variable VALIDATION_ENDPOINT"
                    exit 1
                  fi
                  exit 0
                fi
                if [ "$1" = "test" ]; then
                  if grep -q "test-failed" "$config"; then
                    echo "test valid ... passed"
                    echo "test invalid ... failed"
                    exit 1
                  fi
                  echo "test valid ... passed"
                  echo "test invalid ... passed"
                  exit 0
                fi
                exit 2
                """);
        Files.setPosixFilePermissions(vector,
                PosixFilePermissions.fromString("rwx------"));
        return vector;
    }

    private Set<String> validationDirectories() throws IOException {
        Path root = Path.of(System.getProperty("java.io.tmpdir"));
        try (var paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString()
                            .startsWith("vectum-validation-"))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
    }
}
