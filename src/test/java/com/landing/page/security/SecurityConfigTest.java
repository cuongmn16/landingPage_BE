package com.landing.page.security;

import com.landing.page.config.SecurityConfig;
import com.landing.page.controller.AuthController;
import com.landing.page.controller.EmployeeController;
import com.landing.page.controller.LeaderboardController;
import com.landing.page.controller.MissionController;
import com.landing.page.controller.SubmissionController;
import com.landing.page.entity.Employee;
import com.landing.page.entity.enums.Role;
import com.landing.page.repository.EmployeeRepository;
import com.landing.page.service.EmailService;
import com.landing.page.service.EmployeeService;
import com.landing.page.service.LeaderboardService;
import com.landing.page.service.MinioService;
import com.landing.page.service.MissionService;
import com.landing.page.service.OtpService;
import com.landing.page.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        EmployeeController.class,
        MissionController.class,
        SubmissionController.class,
        LeaderboardController.class,
        AuthController.class
})
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-test-secret",
        "app.cors.allowed-origins=http://localhost:5173"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockitoBean private StringRedisTemplate redisTemplate;
    @MockitoBean private EmployeeService employeeService;
    @MockitoBean private MissionService missionService;
    @MockitoBean private SubmissionService submissionService;
    @MockitoBean private MinioService minioService;
    @MockitoBean private LeaderboardService leaderboardService;
    @MockitoBean private OtpService otpService;
    @MockitoBean private EmailService emailService;
    @MockitoBean private EmployeeRepository employeeRepository;

    private String accessToken(Role role) {
        Employee e = Employee.builder().id(1L).email("u@ctin.vn").employeeCode("CT1").role(role).build();
        return "Bearer " + tokenProvider.generateAccessToken(e);
    }

    @Test
    void publicEndpointsAreOpen() throws Exception {
        when(missionService.getAllMissions()).thenReturn(List.of());
        when(leaderboardService.getUnitLeaderboard()).thenReturn(List.of());

        mvc.perform(get("/api/missions")).andExpect(status().isOk());
        mvc.perform(get("/api/leaderboard/units")).andExpect(status().isOk());
    }

    @Test
    void adminEndpointsRequireLogin() throws Exception {
        mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/employees/1/approve")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/submissions/all")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/missions/1/toggle").param("isOpen", "false")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsRejectNormalUsers() throws Exception {
        String user = accessToken(Role.ROLE_USER);
        mvc.perform(get("/api/employees").header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(put("/api/employees/1/approve").header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(put("/api/submissions/1/status").param("status", "VALID").header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAdminEndpoints() throws Exception {
        when(employeeService.getAllEmployees()).thenReturn(List.of());
        mvc.perform(get("/api/employees").header("Authorization", accessToken(Role.ROLE_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void userCanReadOwnSubmissions() throws Exception {
        when(submissionService.getMySubmissions()).thenReturn(List.of());
        mvc.perform(get("/api/submissions/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/submissions/me").header("Authorization", accessToken(Role.ROLE_USER)))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() throws Exception {
        Employee admin = Employee.builder().id(1L).email("a@ctin.vn").employeeCode("A1").role(Role.ROLE_ADMIN).build();
        String refresh = tokenProvider.generateRefreshToken(admin);
        mvc.perform(get("/api/employees").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fileTokenIsBoundToObjectKey() {
        String token = tokenProvider.generateFileToken("submissions/a.pdf");
        assertThat(tokenProvider.validateFileToken(token, "submissions/a.pdf")).isTrue();
        assertThat(tokenProvider.validateFileToken(token, "submissions/b.pdf")).isFalse();
        assertThat(tokenProvider.validateFileToken("garbage", "submissions/a.pdf")).isFalse();
    }
}
