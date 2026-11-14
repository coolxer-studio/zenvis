package com.coolxer.controller.config;

import com.coolxer.model.base.vo.ResponseWrap;
import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigPathOperationResult;
import com.coolxer.service.config.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigControllerTest {

    private ConfigService configService;
    private ConfigController controller;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        controller = new ConfigController();
        ReflectionTestUtils.setField(controller, "configService", configService);
    }

    @Test
    void deleteDefaultsToNonRecursive() {
        ConfigDto configDto = new ConfigDto();
        configDto.setFileName("archive");
        when(configService.fileExistsInConfigPath("html-page", "archive")).thenReturn(true);
        when(configService.deleteEntry("html-page", "archive", false))
                .thenReturn(new ConfigPathOperationResult().setSuccess(true));

        ResponseWrap<String> response = controller.deleteFile("html-page", configDto);

        assertThat(response.getStatus()).isZero();
        verify(configService).deleteEntry("html-page", "archive", false);
    }

    @Test
    void deletePassesRecursiveFlagAndReturnsOperationMessage() {
        ConfigDto configDto = new ConfigDto();
        configDto.setFileName("archive");
        configDto.setRecursive(true);
        when(configService.fileExistsInConfigPath("html-page", "archive")).thenReturn(true);
        when(configService.deleteEntry("html-page", "archive", true))
                .thenReturn(ConfigPathOperationResult.failure(
                        "delete", "archive", "目录非空，必须显式设置 recursive=true 才能递归删除"));

        ResponseWrap<String> response = controller.deleteFile("html-page", configDto);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMsg()).isEqualTo("目录非空，必须显式设置 recursive=true 才能递归删除");
    }
}
