package com.apptracer.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI appTracerOpenApi() {
    return new OpenAPI().info(new Info().title("AppTracer API").version("v1"));
  }
}
