package com.landing.page.config;

import com.landing.page.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String USER = "USER";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOriginsConfig;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public: authentication & self-registration
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/send-otp",
                                "/api/auth/verify-otp", "/api/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/employees/register").permitAll()

                        // Public: read-only landing page data
                        .requestMatchers(HttpMethod.GET, "/api/missions", "/api/missions/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/leaderboard/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/employees/units").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/rule-config/**").permitAll()

                        // File download is authorized by a signed, short-lived token in the URL
                        .requestMatchers(HttpMethod.GET, "/api/submissions/files/download").permitAll()

                        // Logged-in participants (admin accounts cannot submit)
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/submissions").hasRole(USER)
                        .requestMatchers(HttpMethod.GET, "/api/submissions/me").hasRole(USER)
                        .requestMatchers(HttpMethod.GET, "/api/employees/me/valid-status").hasRole(USER)

                        // Everything else under /api is admin-only (BTC)
                        .requestMatchers("/api/**").hasRole(ADMIN)
                        .anyRequest().denyAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "Bạn cần đăng nhập (hoặc phiên đăng nhập đã hết hạn)."))
                        .accessDeniedHandler((request, response, e) ->
                                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "Bạn không có quyền thực hiện thao tác này."))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeJsonError(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"data\":null}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
