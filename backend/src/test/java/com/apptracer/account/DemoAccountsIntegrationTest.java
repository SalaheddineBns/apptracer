package com.apptracer.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.account.service.DemoAccountsInitializer;
import com.apptracer.account.service.InitialAdminInitializer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.demo.password=" + DemoAccountsIntegrationTest.PASSWORD)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoAccountsIntegrationTest {

  static final String PASSWORD = "Demo-accounts-pw-42";

  @Autowired private InitialAdminInitializer adminInitializer;
  @Autowired private DemoAccountsInitializer initializer;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mockMvc;

  @BeforeEach
  @AfterEach
  void clean() {
    jdbc.execute("DELETE FROM time_entries");
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("UPDATE app_settings SET updated_by = NULL");
    jdbc.execute("DELETE FROM users");
  }

  @Test
  void createsTheThreeAccountsAndTheirTeamOnceThenDoesNothing() throws Exception {
    adminInitializer.createIfAbsent();
    Long adminId = users.findByEmail("initial-admin@example.test").orElseThrow().getId();

    assertThat(initializer.createMissing()).isEqualTo(3);
    assertThat(initializer.createTeamIfAbsent()).isTrue();

    Long managerId = users.findByEmail("manager@apptracer.local").orElseThrow().getId();
    Map<String, Object> team =
        jdbc.queryForMap(
            "SELECT id, manager_id, created_by FROM teams WHERE name = ? AND deleted_at IS NULL",
            DemoAccountsInitializer.TEAM_NAME);
    assertThat(team.get("manager_id")).isEqualTo(managerId);
    assertThat(team.get("created_by")).isEqualTo(adminId);
    List<String> memberEmails =
        jdbc.queryForList(
            "SELECT u.email FROM team_members m JOIN users u ON u.id = m.user_id"
                + " WHERE m.team_id = ? ORDER BY u.email",
            String.class,
            team.get("id"));
    assertThat(memberEmails).containsExactly("dev@apptracer.local", "qa@apptracer.local");
    assertThat(
            jdbc.queryForObject(
                "SELECT j.code FROM users u JOIN job_titles j ON j.id = u.job_title_id"
                    + " WHERE u.email = 'qa@apptracer.local'",
                String.class))
        .isEqualTo("QA");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

    // Second démarrage : rien n'est recréé
    assertThat(initializer.createMissing()).isZero();
    assertThat(initializer.createTeamIfAbsent()).isFalse();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM teams", Integer.class)).isEqualTo(1);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dev@apptracer.local\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk());
  }
}
