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
 * Socle de sécurité : API sans session, tout est protégé sauf la documentation OpenAPI.
 * Authentification par access token JWT (RG-03-06, RG-03-09).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  /** RG-03-03 : mots de passe hachés en BCrypt. */
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
                    // Le refresh token est lui-même le justificatif (RG-03-07, RG-03-08)
                    .requestMatchers(
                        "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    // Application Angular servie par l'API (fichiers statiques et routes du front)
                    .anyRequest()
                    .permitAll())
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    // RG-03-09 : token absent, invalide ou expiré, 401 / 403 en ProblemDetail
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(entryPoint) // RG-03-09 : 401
                    .accessDeniedHandler(accessDeniedHandler)) // RG-03-09 : 403
        .build();
  }
}
