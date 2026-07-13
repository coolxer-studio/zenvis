package com.coolxer.aop;

import com.coolxer.configuration.CustomWebConfig;
import com.coolxer.dao.mysql.entity.User;
import com.coolxer.dao.mysql.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorityInterceptorTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private CustomWebConfig customWebConfig;

    @Mock
    private UserRepository userRepository;

    private AuthorityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthorityInterceptor();
        ReflectionTestUtils.setField(interceptor, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(interceptor, "customWebConfig", customWebConfig);
        ReflectionTestUtils.setField(interceptor, "userRepository", userRepository);
        ReflectionTestUtils.setField(interceptor, "apiBearerToken", "api-token");
        ReflectionTestUtils.setField(interceptor, "apiBearerUser", "api@zenvis.local");
    }

    @Test
    void shouldAuthenticateApiRequestWithBearerToken() throws Exception {
        when(customWebConfig.getNeedCheckPath()).thenReturn("/api/v1");
        User user = new User();
        user.setId(7);
        user.setName("API User");
        when(userRepository.findByEmail("api@zenvis.local")).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/user/list");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer api-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(request.getAttribute(AuthorityInterceptor.API_BEARER_USER_ID_ATTR)).isEqualTo(7);
        assertThat(request.getAttribute(AuthorityInterceptor.API_BEARER_USER_NAME_ATTR)).isEqualTo("API User");
    }

    @Test
    void shouldRejectInvalidBearerTokenBeforeSessionFallback() throws Exception {
        when(customWebConfig.getNeedCheckPath()).thenReturn("/api/v1");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/user/list");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getContentAsString()).contains("\"status\":101");
    }

    @Test
    void shouldKeepSessionRequirementWhenBearerTokenIsAbsent() throws Exception {
        when(customWebConfig.getNeedCheckPath()).thenReturn("/api/v1");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/user/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getContentAsString()).contains("\"status\":101");
    }
}
