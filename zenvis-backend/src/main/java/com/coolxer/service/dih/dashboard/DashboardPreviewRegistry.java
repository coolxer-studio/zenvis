package com.coolxer.service.dih.dashboard;

import com.coolxer.model.dih.dashboard.DashboardPackage;
import com.coolxer.model.dih.dashboard.DashboardApplyResult;
import com.coolxer.model.dih.dashboard.DashboardValidationResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DashboardPreviewRegistry {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final Clock clock;
    private final Map<String, PreviewSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, AppliedSession> appliedSessions = new ConcurrentHashMap<>();

    public DashboardPreviewRegistry() {
        this(Clock.systemUTC());
    }

    DashboardPreviewRegistry(Clock clock) {
        this.clock = clock;
    }

    public void put(String workflowId,
                    String chatId,
                    Integer userId,
                    String previewToken,
                    DashboardValidationResult validation,
                    DashboardPackage packageValue,
                    Map<String, String> previewFiles) {
        if (workflowId == null || userId == null || chatId == null
                || previewToken == null || validation == null || packageValue == null) {
            return;
        }
        sessions.put(workflowId, new PreviewSession(
                workflowId, chatId, userId, previewToken,
                validation.getCandidateDigest(), validation.getValidationDigest(),
                validation, packageValue,
                new LinkedHashMap<>(previewFiles == null ? Map.of() : previewFiles),
                clock.instant().plus(TTL)));
        appliedSessions.remove(workflowId);
        purgeExpired();
    }

    public Optional<PreviewSession> get(String workflowId,
                                        Integer userId,
                                        String chatId,
                                        String previewToken) {
        PreviewSession session = sessions.get(workflowId);
        if (session == null || session.expiresAt().isBefore(clock.instant())) {
            if (session != null) {
                sessions.remove(workflowId);
            }
            return Optional.empty();
        }
        if (!session.userId().equals(userId)
                || chatId == null || !chatId.equals(session.chatId())
                || previewToken == null || !session.previewToken().equals(previewToken)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public Optional<PreviewSession> getForApply(String workflowId,
                                                Integer userId,
                                                String chatId) {
        PreviewSession session = sessions.get(workflowId);
        if (session == null || session.expiresAt().isBefore(clock.instant())
                || !session.userId().equals(userId)
                || chatId == null || !session.chatId().equals(chatId)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public String newPreviewToken() {
        return UUID.randomUUID().toString();
    }

    public void markApplied(String workflowId, DashboardApplyResult result) {
        PreviewSession session = sessions.get(workflowId);
        if (session == null || result == null
                || !"verified".equals(result.getStatus())) {
            return;
        }
        appliedSessions.put(workflowId, new AppliedSession(
                workflowId,
                session.chatId(),
                session.userId(),
                session.candidateDigest(),
                session.validationDigest(),
                result,
                session.expiresAt()));
        purgeExpired();
    }

    public Optional<DashboardApplyResult> getApplied(String workflowId,
                                                     Integer userId,
                                                     String chatId,
                                                     String candidateDigest,
                                                     String validationDigest) {
        AppliedSession applied = appliedSessions.get(workflowId);
        if (applied == null || applied.expiresAt().isBefore(clock.instant())) {
            if (applied != null) {
                appliedSessions.remove(workflowId);
            }
            return Optional.empty();
        }
        if (!applied.userId().equals(userId)
                || chatId == null || !chatId.equals(applied.chatId())
                || !applied.candidateDigest().equals(candidateDigest)
                || !applied.validationDigest().equals(validationDigest)) {
            return Optional.empty();
        }
        return Optional.of(applied.result());
    }

    public void remove(String workflowId) {
        if (workflowId != null) {
            sessions.remove(workflowId);
            appliedSessions.remove(workflowId);
        }
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        appliedSessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record PreviewSession(
            String workflowId,
            String chatId,
            Integer userId,
            String previewToken,
            String candidateDigest,
            String validationDigest,
            DashboardValidationResult validation,
            DashboardPackage packageValue,
            Map<String, String> previewFiles,
            Instant expiresAt
    ) {
    }

    private record AppliedSession(
            String workflowId,
            String chatId,
            Integer userId,
            String candidateDigest,
            String validationDigest,
            DashboardApplyResult result,
            Instant expiresAt
    ) {
    }
}
