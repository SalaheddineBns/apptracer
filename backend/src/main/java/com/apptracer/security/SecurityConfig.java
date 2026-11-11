package com.apptracer.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security foundation: stateless API, everything is protected except the OpenAPI documentation.
 * Authentication by JWT access token (RG-03-06, RG-03-09).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  /** RG-03-03: passwords hashed with BCrypt. */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ProblemDetailAuthenticationEntryPoint entryPoint,
      ProblemDetailAccessDeniedHandler accessDeniedHandler,
      JwtAuthenticationConverter jwtAuthenticationConverter)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    // The refresh token is itself the credential (RG-03-07, RG-03-08)
                    .requestMatchers(
                        "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    // Angular app served by the API (static files and front-end routes)
                    .anyRequest()
                    .permitAll())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    // RG-03-09: missing, invalid or expired token, 401 / 403 as a ProblemDetail
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(entryPoint) // RG-03-09 : 401
                    .accessDeniedHandler(accessDeniedHandler)) // RG-03-09 : 403
        .build();
  }
}
