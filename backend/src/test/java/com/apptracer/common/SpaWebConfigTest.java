package com.apptracer.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The API serves the Angular app (demo image) without opening the API to anonymous calls. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpaWebConfigTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void aFrontRouteReturnsTheAngularShellWithoutToken() throws Exception {
    mockMvc
        .perform(get("/team/members/3"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl(null))
        .andExpect(content().string(containsString("AppTracer test shell")));
  }

  @Test
  void theRootForwardsToTheAngularShell() throws Exception {
    // Spring Boot welcome page: forward to index.html, followed by the servlet container
    mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
  }

  @Test
  void aMissingFileIsNotReplacedByTheShell() throws Exception {
    mockMvc.perform(get("/missing-chunk.js")).andExpect(status().isNotFound());
  }

  @Test
  void theApiStillRequiresAToken() throws Exception {
    mockMvc.perform(get("/api/v1/time-entries")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/unknown")).andExpect(status().isUnauthorized());
  }
}
