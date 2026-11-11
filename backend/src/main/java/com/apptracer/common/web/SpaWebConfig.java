package com.apptracer.common.web;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the Angular app built into {@code classpath:/static/} (demo Docker image). A front-end
 * route ({@code /journal}, {@code /team/members/3}…) returns {@code index.html}, and the Angular
 * router takes over. Missing API and documentation paths stay 404. In development, without {@code
 * static/}, nothing is served.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(
            new PathResourceResolver() {
              @Override
              protected Resource getResource(String path, Resource location) throws IOException {
                Resource requested = location.createRelative(path);
                if (requested.exists() && requested.isReadable()) {
                  return requested;
                }
                if (path.startsWith("api/") || path.startsWith("v3/") || path.contains(".")) {
                  return null;
                }
                Resource index = location.createRelative("index.html");
                return index.exists() && index.isReadable() ? index : null;
              }
            });
  }
}
