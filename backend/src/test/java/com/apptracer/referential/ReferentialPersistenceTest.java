package com.apptracer.referential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.Project;
import com.apptracer.referential.domain.TagColor;
import com.apptracer.referential.repository.ActivityTypeRepository;
import com.apptracer.referential.repository.ProjectRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReferentialPersistenceTest {

  private static final List<String> INITIAL_CODES =
      List.of("TRAVAIL_PROJET", "REUNION", "LECTURE_SFD", "REVUE_MR", "FORMATION", "AUTRE");

  @Autowired private ActivityTypeRepository types;
  @Autowired private ProjectRepository projects;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    // seules les lignes créées par les tests sont retirées : le jeu initial est conservé
    jdbc.execute(
        "DELETE FROM activity_types WHERE code NOT IN ('"
            + String.join("','", INITIAL_CODES)
            + "')");
    jdbc.execute("DELETE FROM projects");
    jdbc.execute("DELETE FROM users WHERE email = 'ref-admin@example.com'");
  }

  @Test
  @DisplayName("CA-02-01 : une installation neuve contient les six types initiaux, actifs")
  void initialTypesAreSixAndActive() {
    List<ActivityType> all = types.findAll();
    assertThat(all)
        .extracting(ActivityType::getCode)
        .containsExactlyInAnyOrderElementsOf(INITIAL_CODES);
    assertThat(all).allMatch(ActivityType::isActive); // RG-02-02
  }

  @Test
  @DisplayName("CA-02-02 : TRAVAIL_PROJET et REVUE_MR exigent un projet, les quatre autres non")
  void projectRequiredOnlyForTwoTypes() {
    for (String code : INITIAL_CODES) {
      boolean expected = code.equals("TRAVAIL_PROJET") || code.equals("REVUE_MR");
      assertThat(types.findByCode(code).orElseThrow().isProjectRequired())
          .as(code)
          .isEqualTo(expected); // RG-02-03
    }
  }

  @Test
  @DisplayName("RG-02-04 : la couleur des types initiaux suit le design system")
  void initialColors() {
    assertThat(types.findByCode("TRAVAIL_PROJET").orElseThrow().getColor())
        .isEqualTo(TagColor.INFO);
    assertThat(types.findByCode("REUNION").orElseThrow().getColor()).isEqualTo(TagColor.WARN);
    assertThat(types.findByCode("LECTURE_SFD").orElseThrow().getColor())
        .isEqualTo(TagColor.SECONDARY);
    assertThat(types.findByCode("REVUE_MR").orElseThrow().getColor()).isEqualTo(TagColor.CONTRAST);
    assertThat(types.findByCode("FORMATION").orElseThrow().getColor()).isEqualTo(TagColor.SUCCESS);
    assertThat(types.findByCode("AUTRE").orElseThrow().getColor()).isEqualTo(TagColor.SECONDARY);
  }

  @Test
  @DisplayName("RG-00-04 : les types initiaux sont des créations système (created_by nul)")
  void initialTypesAreSystemCreated() {
    assertThat(types.findAll())
        .allSatisfy(
            t -> {
              assertThat(t.getCreatedAt()).isNotNull();
              assertThat(t.getUpdatedAt()).isNotNull();
              assertThat(t.getCreatedBy()).isNull();
              assertThat(t.getUpdatedBy()).isNull();
            });
  }

  @Test
  @DisplayName("RG-00-04 : l'audit JPA alimente created_by et updated_by sur un projet")
  void auditColumnsOnProject() {
    User admin =
        users.saveAndFlush(new User("ref-admin@example.com", "A", "B", "hash", Role.ADMIN));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                String.valueOf(admin.getId()), null, List.of()));
    Project p = projects.saveAndFlush(new Project("P1", "Projet 1"));
    assertThat(p.getCreatedBy()).isEqualTo(admin.getId());
    assertThat(p.getUpdatedBy()).isEqualTo(admin.getId());
    assertThat(p.isActive()).isTrue(); // RG-02-05
    jdbc.update("DELETE FROM projects");
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("RG-02-01 : un code de type déjà utilisé est refusé par la base")
  void duplicateTypeCodeRejected() {
    assertThatThrownBy(
            () -> types.saveAndFlush(new ActivityType("REUNION", "Autre", false, TagColor.INFO)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "RG-02-01 : un code de projet déjà utilisé est refusé, mais un type et un projet peuvent partager un code")
  void codeUniqueWithinOwnReferential() {
    projects.saveAndFlush(new Project("REUNION", "Projet homonyme"));
    assertThat(projects.existsByCode("REUNION")).isTrue();
    assertThatThrownBy(() -> projects.saveAndFlush(new Project("REUNION", "Doublon")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("RG-02-01 : la base refuse un code hors du format [A-Z0-9_]{2,30}")
  void invalidCodeFormatRejected() {
    for (String bad : List.of("reunion client", "ab", "A", "A".repeat(31), "ÉTÉ", "A-B", "a_b")) {
      assertThatThrownBy(() -> types.saveAndFlush(new ActivityType(bad, "x", false, TagColor.INFO)))
          .as("type %s", bad)
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> projects.saveAndFlush(new Project(bad, "x")))
          .as("projet %s", bad)
          .isInstanceOf(DataIntegrityViolationException.class);
    }
    // bornes acceptées
    types.saveAndFlush(new ActivityType("A1", "Min", false, TagColor.INFO));
    types.saveAndFlush(new ActivityType("A_".repeat(15), "Max", false, TagColor.INFO));
  }

  @Test
  @DisplayName("RG-02-04 : la base refuse une couleur hors palette")
  void colorOutsidePaletteRejected() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO activity_types (code, label, project_required, color, created_at, updated_at)"
                        + " VALUES ('DANGER_T','x',false,'DANGER', now(), now())"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("RG-02-05 : un type est désactivé puis réactivé, jamais supprimé")
  void deactivateAndReactivate() {
    ActivityType t = types.saveAndFlush(new ActivityType("TEMP_T", "Temp", false, TagColor.WARN));
    assertThat(t.isActive()).isTrue();
    t.setActive(false);
    ActivityType off = types.saveAndFlush(t);
    assertThat(off.isActive()).isFalse();
    off.setActive(true);
    assertThat(types.saveAndFlush(off).isActive()).isTrue();
    assertThat(types.findByCode("TEMP_T").orElseThrow().getVersion()).isEqualTo(2L);
  }
}
