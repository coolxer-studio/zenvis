package com.coolxer.plugin.operation.service;

import com.coolxer.plugin.operation.model.OperationBoardRecord;
import com.coolxer.plugin.operation.repository.OperationBoardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OperationBoardServiceTest {

    @Test
    void deleteReconnectsPreviousAndNextBoards() {
        OperationBoardRecord middle = new OperationBoardRecord(
                2L, 1L, 3L, null, null, null, null, null, null, null);
        RecordingRepository repository = new RecordingRepository(middle);
        OperationBoardService service = new OperationBoardService(repository, new ObjectMapper());

        service.delete(2L);

        assertThat(repository.updatedNextId).isEqualTo(1L);
        assertThat(repository.updatedNextValue).isEqualTo(3L);
        assertThat(repository.updatedLastId).isEqualTo(3L);
        assertThat(repository.updatedLastValue).isEqualTo(1L);
        assertThat(repository.deletedId).isEqualTo(2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lineChartKeepsTheOriginalTwoSeriesPayload() {
        OperationBoardRecord board = new OperationBoardRecord(
                1L, null, null, null, null, null, null, null, null, "折线图");
        OperationBoardService service =
                new OperationBoardService(new RecordingRepository(board), new ObjectMapper());

        Map<String, Object> chart = (Map<String, Object>) service.getChartById(1L);
        List<Map<String, Object>> series = (List<Map<String, Object>>) chart.get("series");

        assertThat(series).extracting(item -> item.get("name"))
                .containsExactly("日活跃用户", "周活跃用户");
    }

    private static class RecordingRepository extends OperationBoardRepository {
        private final OperationBoardRecord record;
        private Long updatedNextId;
        private Long updatedNextValue;
        private Long updatedLastId;
        private Long updatedLastValue;
        private Long deletedId;

        RecordingRepository(OperationBoardRecord record) {
            super(null, new ObjectMapper());
            this.record = record;
        }

        @Override
        public Optional<OperationBoardRecord> findById(long id) {
            return record.id() == id ? Optional.of(record) : Optional.empty();
        }

        @Override
        public void updateNext(long id, Long nextBoard) {
            updatedNextId = id;
            updatedNextValue = nextBoard;
        }

        @Override
        public void updateLast(long id, Long lastBoard) {
            updatedLastId = id;
            updatedLastValue = lastBoard;
        }

        @Override
        public void deleteById(long id) {
            deletedId = id;
        }
    }
}
