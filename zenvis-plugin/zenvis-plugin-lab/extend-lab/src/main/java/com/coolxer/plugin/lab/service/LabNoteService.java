package com.coolxer.plugin.lab.service;

import com.coolxer.plugin.lab.api.LabApiException;
import com.coolxer.plugin.lab.api.PageRows;
import com.coolxer.plugin.lab.model.LabNote;
import com.coolxer.plugin.lab.model.LabNoteRequests;
import com.coolxer.plugin.lab.model.LabNoteStats;
import com.coolxer.plugin.lab.repository.LabNoteRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class LabNoteService {

    public static final Set<String> STATUSES = new LinkedHashSet<>(
            java.util.List.of("draft", "published", "archived"));

    private final LabNoteRepository repository;
    private final TransactionTemplate transactionTemplate;

    public LabNoteService(
            LabNoteRepository repository,
            @Qualifier("pluginMysqlTransactionManager") PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    public PageRows<LabNote> search(int page, int perPage, String keyword, String status) {
        int safePage = page < 1 ? 1 : page;
        int safePerPage = Math.min(Math.max(perPage, 1), 100);
        String safeStatus = normalizeStatus(status, false);
        String safeKeyword = normalizeKeyword(keyword);
        return repository.search(safePage, safePerPage, safeKeyword, safeStatus);
    }

    public LabNote detail(long id) {
        if (id <= 0) {
            throw LabApiException.badRequest("笔记 ID 必须为正整数");
        }
        LabNote note = repository.findById(id);
        if (note == null) {
            throw LabApiException.notFound("笔记不存在");
        }
        return note;
    }

    public LabNote create(LabNoteRequests.Upsert request) {
        ValidatedNote note = validate(request, true);
        return inTransaction(() -> repository.insert(note.title(), note.content(), note.status(), note.owner()));
    }

    public LabNote update(long id, LabNoteRequests.Upsert request) {
        if (id <= 0) {
            throw LabApiException.badRequest("笔记 ID 必须为正整数");
        }
        ValidatedNote note = validate(request, false);
        LabNote updated = inTransaction(() -> repository.update(id, note.title(), note.content(), note.status(), note.owner()));
        if (updated == null) {
            throw LabApiException.notFound("笔记不存在");
        }
        return updated;
    }

    public void delete(long id) {
        if (id <= 0) {
            throw LabApiException.badRequest("笔记 ID 必须为正整数");
        }
        Boolean deleted = inTransaction(() -> repository.delete(id));
        if (!Boolean.TRUE.equals(deleted)) {
            throw LabApiException.notFound("笔记不存在");
        }
    }

    public LabNoteStats stats() {
        return repository.stats();
    }

    private ValidatedNote validate(LabNoteRequests.Upsert request, boolean creating) {
        if (request == null) {
            throw LabApiException.badRequest("请求体不能为空");
        }
        String title = requiredText(request.title(), "title", 120);
        String content = request.content() == null ? "" : request.content().trim();
        if (content.length() > 4000) {
            throw LabApiException.badRequest("content 不能超过 4000 个字符");
        }
        String status = normalizeStatus(request.status(), creating);
        if (status == null) {
            throw LabApiException.badRequest("status 不能为空");
        }
        String owner = request.owner() == null || request.owner().isBlank()
                ? "demo" : request.owner().trim();
        if (owner.length() > 80) {
            throw LabApiException.badRequest("owner 不能超过 80 个字符");
        }
        return new ValidatedNote(title, content, status, owner);
    }

    private String requiredText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw LabApiException.badRequest(field + " 不能为空");
        }
        if (normalized.length() > maxLength) {
            throw LabApiException.badRequest(field + " 不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String normalizeStatus(String status, boolean creating) {
        if (status == null || status.isBlank()) {
            return creating ? "draft" : null;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw LabApiException.badRequest("status 只能是 draft、published 或 archived");
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > 100) {
            throw LabApiException.badRequest("keyword 不能超过 100 个字符");
        }
        return "%" + trimmed.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        return transactionTemplate == null ? action.get() : transactionTemplate.execute(status -> action.get());
    }

    private record ValidatedNote(String title, String content, String status, String owner) {
    }
}
