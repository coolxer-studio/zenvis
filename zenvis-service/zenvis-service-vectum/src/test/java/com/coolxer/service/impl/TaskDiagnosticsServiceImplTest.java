package com.coolxer.service.impl;

import com.coolxer.dao.TaskRepository;
import com.coolxer.model.RunMetadata;
import com.coolxer.model.Task;
import com.coolxer.model.diagnostic.RuntimeDiagnostic;
import com.coolxer.model.validation.ValidationDiagnostic;
import com.coolxer.service.VectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskDiagnosticsServiceImplTest {

    @TempDir
    Path workspace;

    private TaskRepository repository;
    private VectorService vectorService;
    private TaskDiagnosticsServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(TaskRepository.class);
        vectorService = mock(VectorService.class);
        service = new TaskDiagnosticsServiceImpl(repository, vectorService, workspace.toString());
        Task task = new Task();
        task.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(task));
    }

    @Test
    void analyzesOnlyCurrentRunAndIgnoresHistoricalErrors() throws Exception {
        String startedAt = Instant.now().minusSeconds(30).toString();
        when(vectorService.runMetadata("7"))
                .thenReturn(new RunMetadata("run-current", startedAt));
        when(vectorService.status("7")).thenReturn("running");
        Path taskDir = workspace.resolve("7");
        Files.createDirectories(taskDir);
        String info = "=== Vectum run run-current started ===\ncurrent run started\ncomponent ready";
        String error = "=== Vectum run run-current started ===\n";
        Files.writeString(taskDir.resolve("info.log"),
                "=== Vectum run run-old started ===\nERROR old connection refused\n" + info);
        Files.writeString(taskDir.resolve("error.log"),
                "=== Vectum run run-old started ===\nERROR old connection refused\n" + error);

        RuntimeDiagnostic diagnosis = service.diagnose(7L);

        assertThat(diagnosis.getVerdict()).isEqualTo("RUNNING");
        assertThat(diagnosis.getFailureStage()).isEqualTo("NONE");
        assertThat(diagnosis.getDiagnostics()).isEmpty();
        assertThat(diagnosis.getLogExcerpt()).doesNotContain("old connection refused");
    }

    @Test
    void classifiesConnectionAuthenticationDnsTlsAndPermissionAsExternal() throws Exception {
        assertExternal("connection refused", "EXTERNAL_CONNECTION_REFUSED");
        assertExternal("authentication failed: 401", "EXTERNAL_AUTHENTICATION");
        assertExternal("dns error: no such host", "EXTERNAL_DNS");
        assertExternal("TLS handshake certificate failure", "EXTERNAL_TLS");
        assertExternal("permission denied: /data", "EXTERNAL_PERMISSION");
        assertExternal("no such file or directory: /cert", "EXTERNAL_PATH_MISSING");
    }

    @Test
    void distinguishesVrlAndConfigurationFailuresAsRepairable() throws Exception {
        RuntimeDiagnostic vrl = diagnoseWithError("error[E203]: failed to compile VRL");
        assertThat(vrl.getFailureStage()).isEqualTo("VRL_ERROR");
        assertThat(vrl.isRepairable()).isTrue();
        assertThat(codes(vrl)).contains("RUNTIME_VRL_ERROR");

        RuntimeDiagnostic config = diagnoseWithError(
                "ERROR configuration error: unknown field batch_size");
        assertThat(config.getFailureStage()).isEqualTo("CONFIG_ERROR");
        assertThat(config.isRepairable()).isTrue();
        assertThat(codes(config)).contains("RUNTIME_CONFIG_ERROR");
    }

    @Test
    void missingRunMetadataOrCurrentMarkerCannotClaimSuccess() throws Exception {
        when(vectorService.runMetadata("7")).thenReturn(new RunMetadata("", ""));
        when(vectorService.status("7")).thenReturn("running");
        writeLogs("run-old", "", "started");
        assertThat(service.diagnose(7L).getVerdict()).isEqualTo("UNAVAILABLE");
        assertThat(service.diagnose(7L).getFailureStage()).isEqualTo("RUN_METADATA_MISSING");

        when(vectorService.runMetadata("7"))
                .thenReturn(new RunMetadata("run-new", Instant.now().toString()));
        RuntimeDiagnostic missingMarker = service.diagnose(7L);
        assertThat(missingMarker.getVerdict()).isEqualTo("UNAVAILABLE");
        assertThat(missingMarker.getFailureStage()).isEqualTo("CURRENT_RUN_LOG_MISSING");
        assertThat(missingMarker.isRepairable()).isFalse();
    }

    @Test
    void reportsStartingAndCrashedStates() throws Exception {
        when(vectorService.runMetadata("7"))
                .thenReturn(new RunMetadata("run-start", Instant.now().toString()));
        when(vectorService.status("7")).thenReturn("running");
        writeLogs("run-start", "", "starting");
        assertThat(service.diagnose(7L).getVerdict()).isEqualTo("STARTING");

        when(vectorService.status("7")).thenReturn("error");
        RuntimeDiagnostic crashed = service.diagnose(7L);
        assertThat(crashed.getVerdict()).isEqualTo("CRASHED");
        assertThat(crashed.getFailureStage()).isEqualTo("PROCESS_CRASH");
        assertThat(codes(crashed)).contains("RUNTIME_PROCESS_FAILURE");
    }

    @Test
    void redactsSecretsFromLogExcerpt() throws Exception {
        when(vectorService.runMetadata("7"))
                .thenReturn(new RunMetadata("run-secret",
                        Instant.now().minusSeconds(30).toString()));
        when(vectorService.status("7")).thenReturn("running[error]");
        writeLogs("run-secret",
                "ERROR password=super-secret bearer abc.def",
                "connection refused https://user:pass@example.invalid");

        RuntimeDiagnostic diagnosis = service.diagnose(7L);

        assertThat(diagnosis.getLogExcerpt())
                .doesNotContain("super-secret", "abc.def", "user:pass")
                .contains("***");
    }

    @Test
    void ordinaryErrorWordInNormalOutputDoesNotMakeRunningTaskFail() throws Exception {
        when(vectorService.runMetadata("7"))
                .thenReturn(new RunMetadata("run-benign",
                        Instant.now().minusSeconds(30).toString()));
        when(vectorService.status("7")).thenReturn("running");
        writeLogs("run-benign", "event payload contains error: retry metadata", "");

        RuntimeDiagnostic diagnosis = service.diagnose(7L);

        assertThat(diagnosis.getVerdict()).isEqualTo("RUNNING");
        assertThat(diagnosis.getFailureStage()).isEqualTo("NONE");
        assertThat(diagnosis.getDiagnostics()).isEmpty();
    }

    private void assertExternal(String errorText, String expectedCode) throws Exception {
        RuntimeDiagnostic diagnosis = diagnoseWithError("ERROR " + errorText);
        assertThat(diagnosis.getFailureStage()).isEqualTo("EXTERNAL_DEPENDENCY");
        assertThat(diagnosis.isRepairable()).isFalse();
        assertThat(codes(diagnosis)).contains(expectedCode);
    }

    private RuntimeDiagnostic diagnoseWithError(String errorText) throws Exception {
        when(vectorService.runMetadata("7"))
                .thenReturn(new RunMetadata("run-error",
                        Instant.now().minusSeconds(30).toString()));
        when(vectorService.status("7")).thenReturn("running[error]");
        writeLogs("run-error", "", errorText);
        return service.diagnose(7L);
    }

    private void writeLogs(String runId, String info, String error) throws Exception {
        Path taskDir = workspace.resolve("7");
        Files.createDirectories(taskDir);
        String marker = "=== Vectum run " + runId + " started ===\n";
        String oldMarker = "=== Vectum run run-old started ===\n";
        Files.writeString(taskDir.resolve("info.log"), oldMarker + marker + info);
        Files.writeString(taskDir.resolve("error.log"), oldMarker + marker + error);
    }

    private Set<String> codes(RuntimeDiagnostic diagnosis) {
        return diagnosis.getDiagnostics().stream()
                .map(ValidationDiagnostic::getCode)
                .collect(Collectors.toSet());
    }
}
