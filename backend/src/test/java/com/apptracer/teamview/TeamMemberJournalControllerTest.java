package com.apptracer.teamview;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.security.AccessTokenService;
import com.apptracer.team.domain.Team;
import com.apptracer.team.domain.TeamMember;
import com.apptracer.team.repository.TeamMemberRepository;
import com.apptracer.team.repository.TeamRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** listTeamMemberTimeEntries: a member's journal for the week (SFD-04, read-only). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeamMemberJournalControllerTest {

  private static final String WEEK = "2026-10-05";
  private static final String URL = "/api/v1/team-view/members/{id}/time-entries";

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository users;
  @Autowired private TeamRepository teams;
  @Autowired private TeamMemberRepository members;
  @Autowired private AccessTokenService tokens;
  @Autowired private JdbcTemplate jdbc;

  private User admin;
  private User plainUser;
  private User manager;
  private User otherManager;
  private User anne;
  private User outsider;
  private Team alpha;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("tv3-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("tv3-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("tv3-mgr@example.com", "Mia", "Manager", "hash", Role.MANAGER));
    otherManager = users.save(new User("tv3-mgr2@example.com", "O", "Other", "hash", Role.MANAGER));
    anne = users.save(new User("tv3-anne@example.com", "Anne", "Adam", "hash", Role.USER));
    outsider = users.save(new User("tv3-out@example.com", "Otto", "Outside", "hash", Role.USER));
    alpha = team("Alpha", manager, anne);
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    jdbc.execute("DELETE FROM time_entries");
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users WHERE email LIKE 'tv3-%'");
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, User u) {
    return b.header(
        HttpHeaders.AUTHORIZATION,
        "Bearer " + tokens.issue(u.getId(), u.getRole(), UUID.randomUUID()));
  }

  private Team team(String name, User teamManager, User... teamMembers) {
    // RG-00-04 : les écritures de mise en place sont attribuées à l'ADMIN
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                String.valueOf(admin.getId()), null, java.util.List.of()));
    try {
      Team team = teams.saveAndFlush(new Team(name, teamManager));
      for (User m : teamMembers) {
        members.saveAndFlush(new TeamMember(m.getId(), team.getId()));
      }
      return team;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private long create(User u, String body) throws Exception {
    mockMvc
        .perform(
            as(post("/api/v1/time-entries"), u)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    return jdbc.queryForObject("SELECT max(id) FROM time_entries", Long.class);
  }

  private static String hour(String date, String from, String to) {
    return ("{\"granularity\":\"HOUR\",\"startDate\":\"%s\",\"startTime\":\"%s\","
            + "\"endTime\":\"%s\",\"activityTypeCode\":\"REUNION\","
            + "\"comment\":\"Point d'équipe\"}")
        .formatted(date, from, to);
  }

  private static String day(String date) {
    return "{\"granularity\":\"DAY\",\"startDate\":\"%s\",\"activityTypeCode\":\"REUNION\"}"
        .formatted(date);
  }

  private ResultActions journal(User caller, Long memberId, String weekStart) throws Exception {
    return mockMvc.perform(as(get(URL, memberId), caller).param("weekStart", weekStart));
  }

  @Test
  @DisplayName("CA-04-05 / RG-04-01 : sans jeton, 401")
  void withoutTokenIs401() throws Exception {
    mockMvc
        .perform(get(URL, anne.getId()).param("weekStart", WEEK))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-04-05 : un USER reçoit 403")
  void userIs403() throws Exception {
    journal(plainUser, anne.getId(), WEEK).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CA-04-06 : un ADMIN reçoit 403")
  void adminIs403() throws Exception {
    journal(admin, anne.getId(), WEEK).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CA-04-02 : le manager voit les saisies de la semaine d'un membre et son total")
  void managerSeesTheMemberJournal() throws Exception {
    create(anne, hour("2026-10-05", "09:00", "10:30"));
    create(anne, day("2026-10-06"));
    journal(manager, anne.getId(), WEEK)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team.id").value(alpha.getId()))
        .andExpect(jsonPath("$.team.name").value("Alpha"))
        .andExpect(jsonPath("$.member.id").value(anne.getId()))
        .andExpect(jsonPath("$.member.firstName").value("Anne"))
        .andExpect(jsonPath("$.member.lastName").value("Adam"))
        .andExpect(jsonPath("$.member.active").value(true))
        .andExpect(jsonPath("$.week.weekStart").value(WEEK))
        .andExpect(jsonPath("$.week.weekEnd").value("2026-10-11"))
        .andExpect(jsonPath("$.week.totalMinutes").value(570))
        .andExpect(jsonPath("$.week.entries.length()").value(2))
        .andExpect(jsonPath("$.week.entries[0].comment").value("Point d'équipe"))
        .andExpect(jsonPath("$.week.entries[0].activityType.code").value("REUNION"))
        .andExpect(jsonPath("$.week.entries[0].durationMinutes").value(90));
  }

  @Test
  @DisplayName("RG-04-05 : la semaine renvoyée est identique à celle du journal du membre")
  void weekIsTheSameAsTheMemberOwnJournal() throws Exception {
    create(anne, hour("2026-10-05", "09:00", "10:30"));
    create(anne, day("2026-10-08"));
    String own =
        mockMvc
            .perform(as(get("/api/v1/time-entries"), anne).param("weekStart", WEEK))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String viaManager =
        journal(manager, anne.getId(), WEEK).andReturn().getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(viaManager).get("week"))
        .isEqualTo(new com.fasterxml.jackson.databind.ObjectMapper().readTree(own));
  }

  @Test
  @DisplayName("CA-04-08 / RG-01-11 : une saisie supprimée n'apparaît pas et ne compte pas")
  void deletedEntryIsNotShown() throws Exception {
    create(anne, hour("2026-10-05", "09:00", "10:30"));
    long second = create(anne, day("2026-10-06"));
    mockMvc
        .perform(as(delete("/api/v1/time-entries/{id}", second), anne))
        .andExpect(status().isNoContent());
    journal(manager, anne.getId(), WEEK)
        .andExpect(jsonPath("$.week.totalMinutes").value(90))
        .andExpect(jsonPath("$.week.entries.length()").value(1));
  }

  @Test
  @DisplayName("RG-04-06 : une saisie ancienne d'un membre actuel est visible")
  void oldEntriesAreVisible() throws Exception {
    create(anne, day("2026-01-05"));
    journal(manager, anne.getId(), "2026-01-05")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.week.entries.length()").value(1));
  }

  @Test
  @DisplayName("CA-04-12 / RG-04-07 : le journal d'un membre inactif reste consultable")
  void inactiveMemberJournalIsReadable() throws Exception {
    create(anne, hour("2026-10-05", "09:00", "10:30"));
    jdbc.update("UPDATE users SET active = false WHERE id = ?", anne.getId());
    journal(manager, anne.getId(), WEEK)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.member.active").value(false))
        .andExpect(jsonPath("$.week.totalMinutes").value(90));
  }

  @Test
  @DisplayName("CA-04-03 : un membre d'une équipe d'un autre manager donne 403 RG-04-02")
  void memberOfAnotherTeamIs403() throws Exception {
    team("Beta", otherManager, outsider);
    create(outsider, day("2026-10-05"));
    journal(manager, outsider.getId(), WEEK)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-04-02"));
  }

  @Test
  @DisplayName("RG-04-02 : un compte sans équipe ou inexistant donne le même 403 (H11)")
  void accountWithoutTeamOrUnknownIs403() throws Exception {
    journal(manager, outsider.getId(), WEEK)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-04-02"));
    journal(manager, 987654L, WEEK)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-04-02"));
  }

  @Test
  @DisplayName("RG-04-10 : le manager ne peut pas lire ses propres saisies par cette API (403)")
  void managerOwnJournalIsNotInScope() throws Exception {
    create(manager, day("2026-10-05"));
    journal(manager, manager.getId(), WEEK)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-04-02"));
  }

  @Test
  @DisplayName("CA-04-11 : un membre retiré de l'équipe donne 403, ses saisies restent intactes")
  void removedMemberIs403() throws Exception {
    create(anne, day("2026-10-05"));
    journal(manager, anne.getId(), WEEK).andExpect(status().isOk());
    jdbc.update("DELETE FROM team_members WHERE user_id = ?", anne.getId());
    journal(manager, anne.getId(), WEEK)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-04-02"));
    mockMvc
        .perform(as(get("/api/v1/time-entries"), anne).param("weekStart", WEEK))
        .andExpect(jsonPath("$.entries.length()").value(1));
  }

  @Test
  @DisplayName("CA-05-12 : après changement de manager, le nouveau voit le membre, l'ancien 403")
  void managerChangeMovesTheScope() throws Exception {
    create(anne, day("2026-10-05"));
    jdbc.update(
        "UPDATE teams SET manager_id = ? WHERE id = ?", otherManager.getId(), alpha.getId());
    journal(otherManager, anne.getId(), WEEK)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.week.totalMinutes").value(480));
    journal(manager, anne.getId(), WEEK)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-04-02"));
  }

  @Test
  @DisplayName("RG-04-02 : le membre d'une équipe supprimée n'est plus dans le périmètre")
  void deletedTeamMemberIs403() throws Exception {
    jdbc.update(
        "UPDATE teams SET deleted_at = now(), deleted_by = ? WHERE id = ?",
        admin.getId(),
        alpha.getId());
    journal(manager, anne.getId(), WEEK).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("RG-04-04 : un weekStart qui n'est pas un lundi donne 400")
  void notAMondayIs400() throws Exception {
    journal(manager, anne.getId(), "2026-10-07")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("RG-04-04 : sans weekStart, 400")
  void missingWeekStartIs400() throws Exception {
    mockMvc.perform(as(get(URL, anne.getId()), manager)).andExpect(status().isBadRequest());
  }
}
