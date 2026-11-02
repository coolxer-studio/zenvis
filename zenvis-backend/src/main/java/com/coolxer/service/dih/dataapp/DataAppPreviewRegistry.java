package com.coolxer.service.dih.dataapp;

import com.coolxer.model.dih.dataapp.DataAppPackage;
import com.coolxer.model.dih.dataapp.DataAppValidationResult;
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
public class DataAppPreviewRegistry {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final Clock clock;
    private final Map<String, PreviewSession> sessions = new ConcurrentHashMap<>();

    public DataAppPreviewRegistry() {
        this(Clock.systemUTC());
    }

    DataAppPreviewRegistry(Clock clock) {
        this.clock = clock;
    }

    public void put(String workflowId,
                    String chatId,
                    Integer userId,
                    String previewToken,
                    DataAppValidationResult validation,
                    DataAppPackage packageValue,
                    Map<String, String> previewFiles) {
        if (workflowId == null || userId == null || previewToken == null
                || chatId == null || validation == null || packageValue == null) {
            return;
        }
        sessions.put(workflowId, new PreviewSession(
                workflowId,
                chatId,
                userId,
                previewToken,
                validation.getCandidateDigest(),
                validation.getValidationDigest(),
                validation,
                packageValue,
                new LinkedHashMap<>(previewFiles == null ? Map.of() : previewFiles),
                clock.instant().plus(TTL)));
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(clock.instant()));
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
                || chatId == null
                || !chatId.equals(session.chatId())
                || previewToken == null
                || !session.previewToken().equals(previewToken)
                || !session.chatId().equals(chatId)) {
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
                || chatId == null
                || !session.chatId().equals(chatId)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public String newPreviewToken() {
        return UUID.randomUUID().toString();
    }

    public void remove(String workflowId) {
        if (workflowId != null) {
            sessions.remove(workflowId);
        }
    }

    public record PreviewSession(
            String workflowId,
            String chatId,
            Integer userId,
            String previewToken,
            String candidateDigest,
            String validationDigest,
            DataAppValidationResult validation,
            DataAppPackage packageValue,
            Map<String, String> previewFiles,
            Instant expiresAt
    ) {
    }
}
