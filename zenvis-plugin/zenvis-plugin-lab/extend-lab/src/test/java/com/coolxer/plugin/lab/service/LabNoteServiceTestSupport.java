package com.coolxer.plugin.lab.service;

import com.coolxer.plugin.lab.api.PageRows;
import com.coolxer.plugin.lab.model.LabNote;
import com.coolxer.plugin.lab.model.LabNoteStats;
import com.coolxer.plugin.lab.repository.LabNoteRepository;

import java.util.LinkedHashMap;
import java.util.List;

public final class LabNoteServiceTestSupport {

    private LabNoteServiceTestSupport() {
    }

    public static LabNoteService emptyService() {
        return new LabNoteService(new EmptyRepository(), null);
    }

    private static final class EmptyRepository extends LabNoteRepository {

        private EmptyRepository() {
            super(null);
        }

        @Override
        public PageRows<LabNote> search(int page, int perPage, String keyword, String status) {
            return new PageRows<>(List.of(), 0);
        }

        @Override
        public LabNoteStats stats() {
            return new LabNoteStats(0, new LinkedHashMap<>());
        }
    }
}
