package com.apptracer.referential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.referential.domain.JobTitle;
import com.apptracer.referential.repository.JobTitleRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JobTitlePersistenceTest {

  private static final List<String> INITIAL_CODES =
      List.of("DEVELOPER", "TECH_LEAD", "QA", "SCRUM_MASTER", "PRODUCT_OWNER", "DATA_ANALYST");

  @Autowired private JobTitleRepository titles;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM users WHERE email = 'jt-persist@example.com'");
    jdbc.execute(
        "DELETE FROM job_titles WHERE code NOT IN ('" + String.join("','", INITIAL_CODES) + "')");
  }

  @Test
  @DisplayName("CA-09-01 : une installation neuve contient les six fonctions initiales, actives")
  void initialTitlesAreSixAndActive() {
    List<JobTitle> all = titles.findAll();
    assertThat(all)
        .extracting(JobTitle::getCode)
        .containsExactlyInAnyOrderElementsOf(INITIAL_CODES); // RG-09-09
    assertThat(all).allMatch(JobTitle::isActive);
    assertThat(titles.findActiveSorted())
        .extracting(JobTitle::getLabel)
        .containsExactly(
            "Data Analyst", "Developer", "Product Owner", "QA", "Scrum Master", "Tech Lead");
  }

  @Test
  @DisplayName("RG-09-05 : le code est unique sans tenir compte de la casse (index en base)")
  void codeUniqueIgnoringCase() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO job_titles (code, label, created_at, updated_at)"
                        + " VALUES ('Tech_Lead', 'x', now(), now())"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(titles.existsByCode("tech_lead")).isTrue();
  }

  @Test
  @DisplayName("RG-09-05 : le format du code est contrôlé en base")
  void codeFormatChecked() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO job_titles (code, label, created_at, updated_at)"
                        + " VALUES ('ux designer', 'x', now(), now())"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("RG-09-02 : un compte porte au plus une fonction, facultative")
  void userCarriesOptionalJobTitle() {
    User user = users.save(new User("jt-persist@example.com", "J", "T", "hash", Role.USER));
    assertThat(users.findById(user.getId()).orElseThrow().getJobTitle()).isNull();
    user.setJobTitle(titles.findByCode("QA").orElseThrow());
    users.saveAndFlush(user);
    assertThat(users.findById(user.getId()).orElseThrow().getJobTitle().getCode()).isEqualTo("QA");
  }

  @Test
  @DisplayName("RG-09-02 : la base refuse une fonction sur un compte ADMIN")
  void adminCannotCarryJobTitle() {
    User admin = users.save(new User("jt-persist@example.com", "J", "T", "hash", Role.ADMIN));
    admin.setJobTitle(titles.findByCode("QA").orElseThrow());
    assertThatThrownBy(() -> users.saveAndFlush(admin))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
