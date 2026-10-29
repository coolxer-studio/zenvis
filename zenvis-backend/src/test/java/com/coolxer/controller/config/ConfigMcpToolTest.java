package com.coolxer.controller.config;

import com.coolxer.model.config.dto.ConfigDto;
import com.coolxer.model.config.vo.ConfigPathOperationResult;
import com.coolxer.service.config.ConfigService;
import com.coolxer.service.config.MetaConfigValidationService;
import com.coolxer.service.retrieval.impl.MetaDataServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigMcpToolTest {

    @TempDir
    Path configRoot;

    private Path metaDir;
    private ConfigService configService;
    private ConfigMcpTool tool;

    @BeforeEach
    void setUp() throws Exception {
        metaDir = configRoot.resolve("meta_config");
        Files.createDirectories(metaDir);
        configService = mock(ConfigService.class);
        when(configService.configPath("meta")).thenReturn(metaDir.toString());
        when(configService.fileExistsInConfigPath("meta", "probe_event.json"))
                .thenReturn(true);
        tool = new ConfigMcpTool(configService);
        ReflectionTestUtils.setField(tool, "metaConfigValidationService",
                new MetaConfigValidationService(configService, new MetaDataServiceImpl()));
    }

    @Test
    void validMetaIsNormalizedAndApplied() {
        ConfigDto configDto = configDto(validMeta());

        assertThat(tool.apply("meta", configDto, null)).isTrue();

        ArgumentCaptor<ConfigDto> modified = ArgumentCaptor.forClass(ConfigDto.class);
        verify(configService).modifyConfig(org.mockito.ArgumentMatchers.eq("meta"),
                modified.capture());
        assertThat(modified.getValue().getText())
                .contains("\"display_selected\" : true")
                .contains("\"column_name\" : \"event_id\"");
        verify(configService).applyConfig("meta", configDto);
    }

    @Test
    void invalidMetaIsRejectedByDefaultWithoutWriting() {
        ConfigDto configDto = configDto(invalidMeta());

        assertThatThrownBy(() -> tool.apply("meta", configDto, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Meta 校验未通过");
        assertThatThrownBy(() -> tool.apply("meta", configDto, false))
                .isInstanceOf(IllegalArgumentException.class);

        verify(configService, never()).modifyConfig(any(), any());
        verify(configService, never()).applyConfig(any(), any());
    }

    @Test
    void forceAllowsExplicitWriteOfInvalidCandidate() {
        ConfigDto configDto = configDto(invalidMeta());

        assertThat(tool.apply("meta", configDto, true)).isTrue();

        verify(configService).modifyConfig("meta", configDto);
        verify(configService).applyConfig("meta", configDto);
    }

    @Test
    void addMetaCreatesEmptyFileWithoutApplyingCandidate() {
        when(configService.fileExistsInConfigPath("meta", "probe_event.json"))
                .thenReturn(false);
        when(configService.addFile("meta", "probe_event.json")).thenReturn(true);
        ConfigDto configDto = configDto(invalidMeta());

        assertThat(tool.addFile("meta", configDto)).isTrue();

        verify(configService).addFile("meta", "probe_event.json");
        verify(configService, never()).modifyConfig(any(), any());
        verify(configService, never()).applyConfig(any(), any());
    }

    @Test
    void strictLoadFailureKeepsWrittenFileAndReturnsFailure() throws Exception {
        Path target = metaDir.resolve("probe_event.json");
        doAnswer(invocation -> {
            ConfigDto configDto = invocation.getArgument(1);
            Files.writeString(target, configDto.getText());
            return null;
        }).when(configService).modifyConfig(org.mockito.ArgumentMatchers.eq("meta"), any());
        doThrow(new IllegalStateException("严格 Meta 加载失败"))
                .when(configService).applyConfig(org.mockito.ArgumentMatchers.eq("meta"), any());

        assertThatThrownBy(() -> tool.apply("meta", configDto(validMeta()), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("严格 Meta 加载失败");

        assertThat(target).exists();
        assertThat(Files.readString(target)).contains("\"probe_event\"");
    }

    @Test
    void renameAndDeletePassLockedPathsAndRecursiveFlagToConfigService() {
        ConfigPathOperationResult renamed = new ConfigPathOperationResult()
                .setSuccess(true)
                .setOperation("rename")
                .setKind("file")
                .setSourcePath("archive/old.json")
                .setTargetPath("archive/new.json")
                .setChanged(true)
                .setApplied(true);
        ConfigPathOperationResult deleted = new ConfigPathOperationResult()
                .setSuccess(true)
                .setOperation("delete")
                .setKind("directory")
                .setSourcePath("archive")
                .setChanged(true)
                .setApplied(true);
        when(configService.renameEntry("meta", "archive/old.json", "new.json"))
                .thenReturn(renamed);
        when(configService.deleteEntry("meta", "archive", true))
                .thenReturn(deleted);

        assertThat(tool.rename("meta", "archive/old.json", "new.json"))
                .isSameAs(renamed);
        assertThat(tool.delete("meta", "archive", true)).isSameAs(deleted);

        verify(configService).renameEntry("meta", "archive/old.json", "new.json");
        verify(configService).deleteEntry("meta", "archive", true);
    }

    private ConfigDto configDto(String text) {
        ConfigDto configDto = new ConfigDto();
        configDto.setFileName("probe_event.json");
        configDto.setText(text);
        return configDto;
    }

    private String validMeta() {
        return validMeta("String");
    }

    private String invalidMeta() {
        return validMeta("NotARealType");
    }

    private String validMeta(String columnType) {
        return """
                {
                  "entity": [{
                    "name": "probe_event",
                    "label": "探针事件",
                    "description": "探针事件描述",
                    "table_name": "zenvis.probe_event",
                    "data_source": "clickhouse",
                    "auto_create": { "engine": "MergeTree", "order_by": ["event_id"] }
                  }],
                  "attribute": [{
                    "entity": "probe_event",
                    "name": "event_id",
                    "label": "事件ID",
                    "description": "事件唯一标识",
                    "column_name": "event_id",
                    "column_type": "%s",
                    "operators": ["equal"]
                  }],
                  "operator": [{ "name": "equal", "label": "等于" }]
                }
                """.formatted(columnType);
    }
}
