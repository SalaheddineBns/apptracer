package com.apptracer.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Exporte le contrat OpenAPI consommé par le front (voir skill contrat-openapi). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiExportTest {

  private static final Path OUTPUT = Path.of("..", "docs", "api", "openapi.json");

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("exporte le contrat OpenAPI dans docs/api/openapi.json")
  void exportsOpenApiContract() throws Exception {
    String json =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    String formatted =
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json));

    Files.createDirectories(OUTPUT.getParent());
    Files.writeString(OUTPUT, formatted + System.lineSeparator(), StandardCharsets.UTF_8);
  }
}
