package com.coolxer.controller.system;

import com.coolxer.model.system.dto.PushTaskDto;
import com.coolxer.model.system.vo.PushTaskVo;
import com.coolxer.service.system.PushTaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PushTaskMcpToolTest {

    private final FakePushTaskService pushTaskService = new FakePushTaskService();
    private final PushTaskMcpTool tool = new PushTaskMcpTool(pushTaskService);

    @Test
    void createAndStartDelegatesToService() {
        PushTaskDto request = new PushTaskDto();
        request.setName("持续分析数据匹配");
        pushTaskService.createAndStartResult = true;

        assertThat(tool.createAndStart(request)).isTrue();

        assertThat(pushTaskService.createAndStartRequest).isSameAs(request);
    }

    @Test
    void listBySourceMarkDelegatesToService() {
        PushTaskVo task = new PushTaskVo();
        task.setName("持续分析数据匹配");
        pushTaskService.findBySourceMarkResult = List.of(task);

        assertThat(tool.listBySourceMark("analysis-risk")).containsExactly(task);

        assertThat(pushTaskService.findBySourceMarkValue).isEqualTo("analysis-risk");
    }

    @Test
    void deleteBySourceMarkDelegatesToService() {
        pushTaskService.deleteBySourceMarkResult = true;

        assertThat(tool.deleteBySourceMark("analysis-risk")).isTrue();

        assertThat(pushTaskService.deleteBySourceMarkValue).isEqualTo("analysis-risk");
    }

    @Test
    void detectFormatDelegatesToService() {
        pushTaskService.detectFormatResult = "yaml";

        assertThat(tool.detectFormat("sources: {}")).isEqualTo("yaml");

        assertThat(pushTaskService.detectFormatContent).isEqualTo("sources: {}");
    }

    private static class FakePushTaskService implements PushTaskService {
        private boolean createAndStartResult;
        private PushTaskDto createAndStartRequest;
        private List<PushTaskVo> findBySourceMarkResult = List.of();
        private String findBySourceMarkValue;
        private boolean deleteBySourceMarkResult;
        private String deleteBySourceMarkValue;
        private String detectFormatResult;
        private String detectFormatContent;

        @Override
        public Object proxy(HttpServletRequest request) {
            return null;
        }

        @Override
        public boolean createAndStart(PushTaskDto pushTaskDto) {
            createAndStartRequest = pushTaskDto;
            return createAndStartResult;
        }

        @Override
        public List<PushTaskVo> findAll() {
            return List.of();
        }

        @Override
        public List<PushTaskVo> findBySourceMark(String sourceMark) {
            findBySourceMarkValue = sourceMark;
            return findBySourceMarkResult;
        }

        @Override
        public boolean deleteBySourceMark(String sourceMark) {
            deleteBySourceMarkValue = sourceMark;
            return deleteBySourceMarkResult;
        }

        @Override
        public String detectFormat(String content) {
            detectFormatContent = content;
            return detectFormatResult;
        }
    }
}
