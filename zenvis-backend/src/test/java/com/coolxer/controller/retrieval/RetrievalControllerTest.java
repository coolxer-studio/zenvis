package com.coolxer.controller.retrieval;

import com.coolxer.dao.mysql.entity.User;
import com.coolxer.model.retrieval.dto.RetrievalRequestDto;
import com.coolxer.model.retrieval.vo.RetrievalRuleConfigVo;
import com.coolxer.model.retrieval.vo.RetrievalRuleDetailVo;
import com.coolxer.model.retrieval.vo.DataListVo;
import com.coolxer.service.retrieval.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RetrievalControllerTest {

    private RetrievalService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RetrievalService.class);
        TestRetrievalController controller = new TestRetrievalController();
        ReflectionTestUtils.setField(controller, "retrievalService", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void updateAcceptsLegacyRuleIdAndReturnsCanonicalIdObject() throws Exception {
        when(service.updateRule(org.mockito.ArgumentMatchers.any(RetrievalRequestDto.class), eq(7))).thenReturn(22);

        mockMvc.perform(post("/api/v1/retrieval/rule/update")
                        .contentType("application/json")
                        .content("{\"rule_id\":22,\"rule_name\":\"renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.id").value(22));

        ArgumentCaptor<RetrievalRequestDto> captor = ArgumentCaptor.forClass(RetrievalRequestDto.class);
        verify(service).updateRule(captor.capture(), eq(7));
        assertThat(captor.getValue().getId()).isEqualTo(22);
    }

    @Test
    void detailUsesSnakeCaseWireContract() throws Exception {
        RetrievalRuleConfigVo config = new RetrievalRuleConfigVo();
        config.setType("normal");
        config.setEntity("asset");
        config.setCriteriaList(List.of());
        config.setCriteriaLogic("and");
        config.setDisplayList(List.of());
        RetrievalRuleDetailVo detail = new RetrievalRuleDetailVo();
        detail.setId(3);
        detail.setName("rule");
        detail.setConfig(config);
        detail.setStatus("valid");
        detail.setIssues(List.of());
        detail.setEntityList(List.of());
        detail.setAttributeList(List.of());
        when(service.getRuleDetail(3, 7)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/retrieval/rule/detail").param("id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.config.criteria_list").isArray())
                .andExpect(jsonPath("$.data.entity_list").isArray())
                .andExpect(jsonPath("$.data.attribute_list").isArray());
    }

    @Test
    void searchAcceptsSingleDisplayField() throws Exception {
        DataListVo<Object> result = new DataListVo<>();
        result.setDataList(List.of());
        when(service.retrievalByCriteria(org.mockito.ArgumentMatchers.any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/retrieval/do")
                        .contentType("application/json")
                        .content("""
                                {
                                  "type":"normal",
                                  "entity":"asset",
                                  "criteria_list":[],
                                  "display_list":[{"entity":"asset","attribute_list":["ip"]}],
                                  "page":1,
                                  "size":10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void deleteAcceptsOnlyCanonicalOrLegacyId() throws Exception {
        when(service.deleteRule(9, 7)).thenReturn(true);

        mockMvc.perform(post("/api/v1/retrieval/rule/delete")
                        .contentType("application/json")
                        .content("{\"rule_id\":9,\"entity\":\"ignored\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0));

        verify(service).deleteRule(9, 7);
    }

    private static class TestRetrievalController extends RetrievalController {
        @Override
        protected User getSessionUser() {
            User user = new User();
            user.setId(7);
            return user;
        }
    }
}
