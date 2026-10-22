package com.coolxer.plugin.lab.controller;

import com.coolxer.plugin.lab.repository.LabNoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static org.assertj.core.api.Assertions.assertThat;

class LabRouteContractTest {

    @Test
    void noteRoutesStayRelativeToPluginPrefixAndUseDocumentedQueryNames() throws Exception {
        assertThat(LabNoteController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/notes");
        assertThat(LabNoteController.class.getMethod(
                        "list", int.class, int.class, String.class, String.class)
                .getParameters()[1].getAnnotation(RequestParam.class).value())
                .isEqualTo("perPage");
        assertThat(LabMcpController.PLUGIN_API_PREFIX)
                .isEqualTo("/api/v1/plugin/com.coolxer.plugin.lab");
        assertThat(LabCapabilityController.class.getMethod("list")
                .getAnnotation(GetMapping.class).value())
                .containsExactly("/capabilities");
    }

    @Test
    void repositoryIsNotProxiedByPersistenceExceptionTranslation() {
        assertThat(LabNoteRepository.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(LabNoteRepository.class.isAnnotationPresent(Repository.class)).isFalse();
    }
}
