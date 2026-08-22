package com.portfoliopilot.config;

import com.portfoliopilot.security.JwtAuthenticationFilter;
import com.portfoliopilot.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT security.
 *
 * <p>Authorization is declared here, in one place, and enforced by the server.
 * The frontend's opinion about a user's role is never consulted - it only
 * decides what to render.
 *
 * <p>Route policy:
 * <pre>
 *   /api/auth/**                 public  (register, login, refresh)
 *   /api/public/**               public  (published portfolios, no auth by design)
 *   /api/templates  (GET)        public  (template picker is not sensitive)
 *   /v3/api-docs, /swagger-ui    public  (lock these down in production)
 *   /api/admin/**                ROLE_ADMIN
 *   everything else under /api   authenticated
 * </pre>
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection is for cookie-based sessions. This API is
                // stateless and authenticates via an Authorization header, which
                // a cross-site form post cannot set - so CSRF adds nothing.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(authenticationEntryPoint))

                .authorizeHttpRequests(auth -> auth
                        // CORS preflight must never require credentials.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // --- public -------------------------------------------------
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/templates").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // --- admin --------------------------------------------------
                        // Declared BEFORE the catch-all so it cannot be shadowed.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // --- everything else ----------------------------------------
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt with the default strength of 10.
     *
     * <p>Must match {@code SEED_BCRYPT_ROUNDS} in the /mongodb seed, otherwise
     * seeded demo accounts cannot log in against this backend. The users
     * collection validator also enforces the bcrypt hash shape, so a plaintext
     * password is rejected by the database itself.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * CORS driven entirely by {@code FRONTEND_URL}. No production domain is
     * hardcoded, and {@code *} is never used.
     *
     * <p>{@code allowCredentials} stays false: the API uses bearer tokens, not
     * cookies, so credentialed CORS is unnecessary - and disabling it keeps the
     * wildcard-origin footgun permanently out of reach.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.originList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
