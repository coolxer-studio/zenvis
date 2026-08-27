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
import org.springframework.data.redis.core.HashOperations;
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

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

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
        assertThat(request.getAttribute(AuthorityInterceptor.AUTHENTICATED_USER_ID_ATTR)).isEqualTo(7);
        assertThat(request.getAttribute(AuthorityInterceptor.AUTHENTICATED_USER_NAME_ATTR)).isEqualTo("API User");
    }

    @Test
    void shouldExposeAuthenticatedSessionUserToDynamicPlugins() throws Exception {
        when(customWebConfig.getNeedCheckPath()).thenReturn("/api/v1");
        when(stringRedisTemplate.hasKey("session-7")).thenReturn(true);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("session-7", "strUid")).thenReturn("7");
        when(hashOperations.get("session-7", "strUname")).thenReturn("Session User");

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/v1/plugin/com.coolxer.plugin.onesoc/security-operation/users");
        request.setRequestedSessionId("session-7");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(request.getAttribute(AuthorityInterceptor.AUTHENTICATED_USER_ID_ATTR)).isEqualTo("7");
        assertThat(request.getAttribute(AuthorityInterceptor.AUTHENTICATED_USER_NAME_ATTR))
                .isEqualTo("Session User");
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

    @Test
    void shouldReleaseOnlyExactBusinessServicePostReportPaths() throws Exception {
        when(customWebConfig.getNeedCheckPath()).thenReturn("/api/v1");

        assertThat(preHandle("POST", "/api/v1/public/business-services/heartbeat")).isTrue();
        assertThat(preHandle("POST", "/api/v1/public/business-services/events")).isTrue();
        assertThat(preHandle("GET", "/api/v1/public/business-services/heartbeat")).isFalse();
        assertThat(preHandle("POST", "/api/v1/public/business-services/heartbeat/extra")).isFalse();
        assertThat(preHandle("GET", "/api/v1/system/business-services/summary")).isFalse();
    }

    private boolean preHandle(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        return interceptor.preHandle(request, response, new Object());
    }
}
