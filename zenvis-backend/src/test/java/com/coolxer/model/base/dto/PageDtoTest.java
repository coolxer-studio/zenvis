package com.coolxer.model.base.dto;

import com.coolxer.configuration.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;

import static org.assertj.core.api.Assertions.assertThat;

class PageDtoTest {

    @Test
    void jsonAcceptsSnakeCasePerPage() throws Exception {
        PageDto pageDto = JacksonConfig.OBJECT_MAPPER.readValue(
                """
                        {
                          "page": 2,
                          "per_page": 25
                        }
                        """,
                PageDto.class
        );

        assertThat(pageDto.getPage()).isEqualTo(2);
        assertThat(pageDto.getPerPage()).isEqualTo(25);
    }

    @Test
    void jsonAcceptsCamelCasePerPage() throws Exception {
        PageDto pageDto = JacksonConfig.OBJECT_MAPPER.readValue(
                """
                        {
                          "page": 3,
                          "perPage": 30
                        }
                        """,
                PageDto.class
        );

        assertThat(pageDto.getPage()).isEqualTo(3);
        assertThat(pageDto.getPerPage()).isEqualTo(30);
    }

    @Test
    void beanBindingAcceptsSnakeCasePerPage() {
        PageDto pageDto = new PageDto();
        BeanWrapperImpl beanWrapper = new BeanWrapperImpl(pageDto);

        beanWrapper.setPropertyValue("per_page", 40);

        assertThat(pageDto.getPerPage()).isEqualTo(40);
    }
}
