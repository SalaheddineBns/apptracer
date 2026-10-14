package com.apptracer.teamview;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** getTeamWeekSummary : totaux de la semaine des membres d'une équipe (SFD-04). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TeamWeekSummaryControllerTest {

  private static final String WEEK = "2026-10-05";

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
  private User paul;
  private User outsider;
  private Team alpha;

  @BeforeEach
  void setUp() {
    admin = users.save(new User("tv2-admin@example.com", "A", "Admin", "hash", Role.ADMIN));
    plainUser = users.save(new User("tv2-user@example.com", "U", "User", "hash", Role.USER));
    manager = users.save(new User("tv2-mgr@example.com", "Mia", "Manager", "hash", Role.MANAGER));
    otherManager = users.save(new User("tv2-mgr2@example.com", "O", "Other", "hash", Role.MANAGER));
    // Paul est créé avant Anne : l'ordre d'insertion ne doit pas dicter l'ordre de la réponse
    paul = users.save(new User("tv2-paul@example.com", "Paul", "Petit", "hash", Role.USER));
    anne = users.save(new User("tv2-anne@example.com", "Anne", "Adam", "hash", Role.USER));
    outsider = users.save(new User("tv2-out@example.com", "Otto", "Outside", "hash", Role.USER));
    alpha = team("Alpha", manager, paul, anne);
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    jdbc.execute("DELETE FROM time_entries");
    jdbc.execute("DELETE FROM team_members");
    jdbc.execute("DELETE FROM teams");
    jdbc.execute("DELETE FROM refresh_tokens");
    jdbc.execute("DELETE FROM users WHERE email LIKE 'tv2-%'");
  }

  private String bearer(User u) {
    return "Bearer " + tokens.issue(u.getId(), u.getRole(), UUID.randomUUID());
  }

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, User u) {
    return b.header(HttpHeaders.AUTHORIZATION, bearer(u));
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
            + "\"endTime\":\"%s\",\"activityTypeCode\":\"REUNION\"}")
        .formatted(date, from, to);
  }

  private static String day(String date) {
    return "{\"granularity\":\"DAY\",\"startDate\":\"%s\",\"activityTypeCode\":\"REUNION\"}"
        .formatted(date);
  }

  private static String range(String from, String to) {
    return ("{\"granularity\":\"RANGE\",\"startDate\":\"%s\",\"endDate\":\"%s\","
            + "\"activityTypeCode\":\"FORMATION\"}")
        .formatted(from, to);
  }

  private ResultActions summary(User caller, Long teamId, String weekStart) throws Exception {
    return mockMvc.perform(
        as(get("/api/v1/team-view/teams/{id}/week", teamId), caller).param("weekStart", weekStart));
  }

  @Test
  @DisplayName("CA-04-05 / RG-04-01 : sans jeton, 401")
  void withoutTokenIs401() throws Exception {
    mockMvc
        .perform(get("/api/v1/team-view/teams/{id}/week", alpha.getId()).param("weekStart", WEEK))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-04-05 : un USER reçoit 403")
  void userIs403() throws Exception {
    summary(plainUser, alpha.getId(), WEEK).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CA-04-06 : un ADMIN reçoit 403")
  void adminIs403() throws Exception {
    summary(admin, alpha.getId(), WEEK).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CA-04-01 : deux membres ayant saisi 90 min et 7 h affichent 1,5 h et 7 h")
  void membersShowTheirTotals() throws Exception {
    create(anne, hour("2026-10-05", "09:00", "10:30"));
    create(paul, day("2026-10-06"));
    summary(manager, alpha.getId(), WEEK)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.team.id").value(alpha.getId()))
        .andExpect(jsonPath("$.team.name").value("Alpha"))
        .andExpect(jsonPath("$.weekStart").value(WEEK))
        .andExpect(jsonPath("$.weekEnd").value("2026-10-11"))
        .andExpect(jsonPath("$.members.length()").value(2))
        // RG-04-09 : Adam avant Petit, malgré l'ordre de création
        .andExpect(jsonPath("$.members[0].member.id").value(anne.getId()))
        .andExpect(jsonPath("$.members[0].totalMinutes").value(90))
        .andExpect(jsonPath("$.members[1].member.id").value(paul.getId()))
        .andExpect(jsonPath("$.members[1].totalMinutes").value(420))
        .andExpect(jsonPath("$.members[0].days.length()").value(7))
        .andExpect(jsonPath("$.members[0].days[0].date").value("2026-10-05"))
        .andExpect(jsonPath("$.members[0].days[0].minutes").value(90))
        .andExpect(jsonPath("$.members[0].days[6].date").value("2026-10-11"))
        .andExpect(jsonPath("$.members[1].days[1].minutes").value(420));
  }

  @Test
  @DisplayName("RG-04-05 : un membre sans saisie est renvoyé avec sept jours à 0")
  void memberWithoutEntryHasSevenZeroDays() throws Exception {
    summary(manager, alpha.getId(), WEEK)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members.length()").value(2))
        .andExpect(jsonPath("$.members[0].totalMinutes").value(0))
        .andExpect(jsonPath("$.members[0].days.length()").value(7))
        .andExpect(jsonPath("$.members[0].days[3].minutes").value(0));
  }

  @Test
  @DisplayName(
      "CA-04-07 : une PLAGE du jeudi 08/10 au mardi 13/10 donne 14 h pour la semaine du 05/10")
  void rangeOverlappingWeekCountsWeekdaysOfTheWeek() throws Exception {
    create(anne, range("2026-10-08", "2026-10-13"));
    summary(manager, alpha.getId(), WEEK)
        .andExpect(jsonPath("$.members[0].totalMinutes").value(840))
        .andExpect(jsonPath("$.members[0].days[3].minutes").value(420))
        .andExpect(jsonPath("$.members[0].days[4].minutes").value(420))
        .andExpect(jsonPath("$.members[0].days[5].minutes").value(0));
    summary(manager, alpha.getId(), "2026-10-12")
        .andExpect(jsonPath("$.members[0].totalMinutes").value(840));
  }

  @Test
  @DisplayName("CA-04-08 : une saisie supprimée n'apparaît pas et ne compte pas dans le total")
  void deletedEntryIsNotCounted() throws Exception {
    create(anne, hour("2026-10-05", "09:00", "10:30"));
    long second = create(anne, day("2026-10-06"));
    mockMvc
        .perform(as(delete("/api/v1/time-entries/{id}", second), anne))
        .andExpect(status().isNoContent());
    summary(manager, alpha.getId(), WEEK)
        .andExpect(jsonPath("$.members[0].totalMinutes").value(90))
        .andExpect(jsonPath("$.members[0].days[1].minutes").value(0));
  }

  @Test
  @DisplayName(
      "CA-04-12 / RG-04-07 : un membre inactif reste affiché, signalé inactif, avec ses totaux")
  void inactiveMemberIsStillListed() throws Exception {
    create(anne, hour("2026-10-05", "09:00", "10:30"));
    jdbc.update("UPDATE users SET active = false WHERE id = ?", anne.getId());
    summary(manager, alpha.getId(), WEEK)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members[0].member.id").value(anne.getId()))
        .andExpect(jsonPath("$.members[0].member.active").value(false))
        .andExpect(jsonPath("$.members[0].totalMinutes").value(90))
        .andExpect(jsonPath("$.members[1].member.active").value(true));
  }

  @Test
  @DisplayName("CA-04-10 : un manager de deux équipes obtient les membres de l'équipe choisie")
  void eachTeamHasItsOwnMembersAndTotals() throws Exception {
    User zoe = users.save(new User("tv2-zoe@example.com", "Zoé", "Zola", "hash", Role.USER));
    Team beta = team("Beta", manager, zoe);
    create(zoe, day("2026-10-07"));
    create(anne, hour("2026-10-05", "09:00", "10:30"));
    summary(manager, beta.getId(), WEEK)
        .andExpect(jsonPath("$.team.name").value("Beta"))
        .andExpect(jsonPath("$.members.length()").value(1))
        .andExpect(jsonPath("$.members[0].member.id").value(zoe.getId()))
        .andExpect(jsonPath("$.members[0].totalMinutes").value(420));
    summary(manager, alpha.getId(), WEEK)
        .andExpect(jsonPath("$.members.length()").value(2))
        .andExpect(jsonPath("$.members[0].totalMinutes").value(90));
  }

  @Test
  @DisplayName("RG-04-10 : les saisies du manager lui-même n'apparaissent pas")
  void managersOwnEntriesAreNotShown() throws Exception {
    create(manager, day("2026-10-05"));
    summary(manager, alpha.getId(), WEEK)
        .andExpect(jsonPath("$.members.length()").value(2))
        .andExpect(jsonPath("$.members[0].totalMinutes").value(0))
        .andExpect(jsonPath("$.members[1].totalMinutes").value(0));
  }

  @Test
  @DisplayName("RG-04-06 : une saisie ancienne d'un membre actuel est visible")
  void oldEntriesOfCurrentMembersAreVisible() throws Exception {
    create(anne, day("2026-01-05"));
    summary(manager, alpha.getId(), "2026-01-05")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members[0].totalMinutes").value(420));
  }

  @Test
  @DisplayName("CA-04-03 : l'équipe d'un autre manager donne 403 RG-04-02")
  void otherManagersTeamIs403() throws Exception {
    Team theirs = team("Theirs", otherManager, outsider);
    summary(manager, theirs.getId(), WEEK)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-04-02"));
  }

  @Test
  @DisplayName("RG-04-02 : une équipe inexistante donne le même 403 RG-04-02 (H11)")
  void unknownTeamHasTheSame403() throws Exception {
    summary(manager, 987654L, WEEK)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-04-02"));
  }

  @Test
  @DisplayName("CA-05-12 : l'ancien manager d'une équipe reçoit 403, le nouveau la voit")
  void formerManagerIsForbiddenNewOneSeesTheTeam() throws Exception {
    jdbc.update(
        "UPDATE teams SET manager_id = ? WHERE id = ?", otherManager.getId(), alpha.getId());
    summary(manager, alpha.getId(), WEEK)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RG-04-02"));
    summary(otherManager, alpha.getId(), WEEK)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members.length()").value(2));
  }

  @Test
  @DisplayName("RG-04-04 : un weekStart qui n'est pas un lundi donne 400")
  void notAMondayIs400() throws Exception {
    summary(manager, alpha.getId(), "2026-10-06").andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("RG-04-04 : sans weekStart, 400")
  void missingWeekStartIs400() throws Exception {
    mockMvc
        .perform(as(get("/api/v1/team-view/teams/{id}/week", alpha.getId()), manager))
        .andExpect(status().isBadRequest());
  }
}
