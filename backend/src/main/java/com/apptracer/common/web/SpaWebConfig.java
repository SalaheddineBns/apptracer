package com.apptracer.common.web;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Sert l'application Angular construite dans {@code classpath:/static/} (image Docker de démo). Une
 * route du front ({@code /journal}, {@code /team/members/3}…) renvoie {@code index.html} : le
 * routeur Angular prend la suite. Les chemins de l'API et de la documentation restent en 404 s'ils
 * n'existent pas. En développement, sans {@code static/}, rien n'est servi.
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
