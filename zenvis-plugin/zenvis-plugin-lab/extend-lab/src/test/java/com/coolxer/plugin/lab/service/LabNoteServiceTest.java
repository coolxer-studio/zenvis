package com.coolxer.plugin.lab.service;

import com.coolxer.plugin.lab.api.LabApiException;
import com.coolxer.plugin.lab.api.PageRows;
import com.coolxer.plugin.lab.model.LabNote;
import com.coolxer.plugin.lab.model.LabNoteRequests;
import com.coolxer.plugin.lab.model.LabNoteStats;
import com.coolxer.plugin.lab.repository.LabNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabNoteServiceTest {

    private FakeNoteRepository repository;
    private LabNoteService service;

    @BeforeEach
    void setUp() {
        repository = new FakeNoteRepository();
        service = new LabNoteService(repository, null);
    }

    @Test
    void clampsPagingAndEscapesLikeWildcards() {
        service.search(0, 500, "50%_demo%", "draft");

        assertThat(repository.lastPage).isEqualTo(1);
        assertThat(repository.lastPerPage).isEqualTo(100);
        assertThat(repository.lastKeyword).isEqualTo("%50!%!_demo!%%");
        assertThat(repository.lastStatus).isEqualTo("draft");
    }

    @Test
    void createsTrimmedNoteWithDefaultOwner() {
        LabNote saved = note(7L, "标题", "draft", "demo");
        repository.created = saved;

        LabNote result = service.create(new LabNoteRequests.Upsert("  标题  ", "  正文  ", null, null));

        assertThat(result).isEqualTo(saved);
        assertThat(repository.lastTitle).isEqualTo("标题");
        assertThat(repository.lastContent).isEqualTo("正文");
        assertThat(repository.lastOwner).isEqualTo("demo");
    }

    @Test
    void rejectsInvalidStatusAndLongTitle() {
        assertThatThrownBy(() -> service.create(new LabNoteRequests.Upsert("标题", "正文", "deleted", null)))
                .isInstanceOf(LabApiException.class)
                .hasMessageContaining("status");
        assertThatThrownBy(() -> service.create(new LabNoteRequests.Upsert("x".repeat(121), "正文", "draft", null)))
                .isInstanceOf(LabApiException.class)
                .hasMessageContaining("120");
    }

    @Test
    void updateAndDeleteReportMissingRecords() {
        assertThatThrownBy(() -> service.update(9L,
                new LabNoteRequests.Upsert("标题", "正文", "published", "owner")))
                .isInstanceOf(LabApiException.class)
                .hasMessage("笔记不存在");
        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(LabApiException.class)
                .hasMessage("笔记不存在");
    }

    private LabNote note(long id, String title, String status, String owner) {
        return new LabNote(id, title, "正文", status, owner,
                LocalDateTime.of(2026, 10, 21, 10, 0),
                LocalDateTime.of(2026, 10, 21, 10, 0));
    }

    private static final class FakeNoteRepository extends LabNoteRepository {

        private int lastPage;
        private int lastPerPage;
        private String lastKeyword;
        private String lastStatus;
        private String lastTitle;
        private String lastContent;
        private String lastOwner;
        private LabNote created;

        private FakeNoteRepository() {
            super(null);
        }

        @Override
        public PageRows<LabNote> search(int page, int perPage, String keyword, String status) {
            lastPage = page;
            lastPerPage = perPage;
            lastKeyword = keyword;
            lastStatus = status;
            return new PageRows<>(List.of(), 0);
        }

        @Override
        public LabNote findById(long id) {
            return id == 9L ? null : new LabNote(id, "标题", "正文", "draft", "demo",
                    LocalDateTime.now(), LocalDateTime.now());
        }

        @Override
        public LabNote insert(String title, String content, String status, String owner) {
            lastTitle = title;
            lastContent = content;
            lastOwner = owner;
            return created;
        }

        @Override
        public LabNote update(long id, String title, String content, String status, String owner) {
            return id == 9L ? null : new LabNote(id, title, content, status, owner,
                    LocalDateTime.now(), LocalDateTime.now());
        }

        @Override
        public boolean delete(long id) {
            return id != 9L;
        }

        @Override
        public LabNoteStats stats() {
            return new LabNoteStats(0, new LinkedHashMap<>());
        }
    }
}
