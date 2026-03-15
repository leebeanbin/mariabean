package com.mariabean.reservation.auth.infrastructure.config;

import com.mariabean.reservation.auth.infrastructure.security.JwtAuthenticationFilter;
import com.mariabean.reservation.auth.infrastructure.oauth2.CustomOAuth2UserService;
import com.mariabean.reservation.auth.infrastructure.oauth2.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final CustomOAuth2UserService customOAuth2UserService;
        private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

        @Value("${app.frontend-url:http://localhost:3000}")
        private String frontendUrl;

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(List.of(frontendUrl));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true);
                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(AbstractHttpConfigurer::disable)
                                .formLogin(AbstractHttpConfigurer::disable)
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .sessionManagement(sessionManagement -> sessionManagement
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(authorize -> authorize
                                                .requestMatchers("/api/v1/auth/**", "/oauth2/**", "/login/**")
                                                .permitAll()
                                                .requestMatchers("/api/v1/public/**").permitAll()
                                                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                                .requestMatchers("/actuator/**").hasRole("ADMIN")
                                                .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                                                "/v3/api-docs/**")
                                                .permitAll()
                                                .requestMatchers(org.springframework.http.HttpMethod.GET,
                                                                "/api/v1/facilities/**",
                                                                "/api/v1/resources/**",
                                                                "/api/v1/search/**")
                                                .permitAll()
                                                .requestMatchers(org.springframework.http.HttpMethod.POST,
                                                                "/api/v1/search/vision",
                                                                "/api/v1/search/vision/url",
                                                                "/api/v1/search/research/click")
                                                .permitAll()
                                                .requestMatchers("/api/v1/admin/search/**").hasRole("ADMIN")
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(customOAuth2UserService))
                                                .successHandler(oAuth2AuthenticationSuccessHandler))
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}
