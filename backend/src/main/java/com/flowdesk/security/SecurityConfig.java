package com.flowdesk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.ratelimit.AuthRateLimitFilter;
import com.flowdesk.ratelimit.RateLimitProperties;
import com.flowdesk.ratelimit.RateLimiterService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Wires up FlowDesk's Spring Security configuration.
 *
 * <p>Two independent authentication paths coexist here, matching the
 * spec's two flow diagrams (Section 8):
 * <ul>
 *   <li><b>Login</b> ({@code auth.service.AuthService}) uses the
 *       {@link AuthenticationManager} bean below, which delegates to
 *       {@link DaoAuthenticationProvider} -&gt; {@link CustomUserDetailsService}
 *       -&gt; {@link PasswordEncoder}. This is a one-time DB hit per login.</li>
 *   <li><b>Every other authenticated request</b> is authenticated by
 *       {@link JwtAuthenticationFilter}, which validates the JWT itself and
 *       never touches {@code UserDetailsService} or the database.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final JwtService jwtService;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private List<String> allowedOrigins;

    public SecurityConfig(
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            JwtService jwtService,
            RateLimiterService rateLimiterService,
            RateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.jwtService = jwtService;
        this.rateLimiterService = rateLimiterService;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(CustomUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless bearer-token API: the browser never automatically
                // attaches the Authorization header the way it does cookies,
                // so there is no cross-site request forgery vector here.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // /logout is intentionally public too: the refresh
                        // token in the request body is the credential being
                        // revoked, and requiring a still-valid access token
                        // just to log out would be user-hostile if it has
                        // already expired.
                        .requestMatchers(
                                "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Unconditionally permitted, but only actually
                        // reachable when springdoc is enabled - see
                        // OpenApiConfig's Javadoc for why that's safe
                        // (disabled entirely in prod, so these 404 there
                        // regardless of this rule).
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                // Ahead of JWT parsing - a request that's going to be
                // rejected for exceeding the auth rate limit shouldn't pay
                // for token validation (or, for /login, a database hit)
                // first. See AuthRateLimitFilter's Javadoc for which
                // endpoints this actually applies to.
                .addFilterBefore(
                        new AuthRateLimitFilter(rateLimiterService, rateLimitProperties, objectMapper),
                        JwtAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
