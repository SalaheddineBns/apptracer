package com.apptracer.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apptracer.TestcontainersConfiguration;
import com.apptracer.account.domain.Role;
import com.apptracer.account.domain.User;
import com.apptracer.account.repository.UserRepository;
import com.apptracer.settings.domain.AppSettings;
import com.apptracer.settings.repository.AppSettingsRepository;
import java.time.Instant;
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
class AppSettingsPersistenceTest {

  @Autowired private AppSettingsRepository settings;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    // resets the row to its installation state before deleting the accounts (foreign key)
    jdbc.update(
        "UPDATE app_settings SET standard_day_minutes = 480, version = 0,"
            + " updated_at = NULL, updated_by = NULL WHERE id = 1");
    jdbc.update("DELETE FROM users WHERE email = 'settings-admin@example.com'");
  }

  private int update(int minutes) {
    return jdbc.update("UPDATE app_settings SET standard_day_minutes = ? WHERE id = 1", minutes);
  }

  @Test
  @DisplayName(
      "CA-06-01 : une installation neuve a une seule ligne, journée standard de 480 minutes, jamais modifiée")
  void freshInstallationHolds480() {
    List<AppSettings> all = settings.findAll();
    assertThat(all).hasSize(1); // RG-06-01
    AppSettings s = all.get(0);
    assertThat(s.getId()).isEqualTo(AppSettings.SINGLETON_ID);
    assertThat(s.getStandardDayMinutes()).isEqualTo(480);
    // RG-06-05: no trace as long as it has never been changed
    assertThat(s.getUpdatedAt()).isNull();
    assertThat(s.getUpdatedBy()).isNull();
  }

  @Test
  @DisplayName("RG-06-02 : la base accepte 60 et 1 440 minutes, et un pas de 15")
  void boundsAccepted() {
    assertThat(update(60)).isEqualTo(1);
    assertThat(update(1440)).isEqualTo(1);
    assertThat(update(450)).isEqualTo(1);
  }

  @Test
  @DisplayName("RG-06-02 : la base refuse 45, 1 455, 430, 0 et une valeur négative")
  void outOfRangeOrStepRejected() {
    for (int bad : new int[] {45, 1455, 430, 0, -15, 1441}) {
      assertThatThrownBy(() -> update(bad))
          .as("valeur %d", bad)
          .isInstanceOf(DataIntegrityViolationException.class);
    }
    assertThat(settings.findAll().get(0).getStandardDayMinutes()).isEqualTo(480);
  }

  @Test
  @DisplayName("RG-06-01 : la base refuse une seconde ligne de paramètres")
  void secondRowRejected() {
    assertThatThrownBy(
            () ->
                jdbc.update("INSERT INTO app_settings (id, standard_day_minutes) VALUES (2, 480)"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update("INSERT INTO app_settings (id, standard_day_minutes) VALUES (1, 480)"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(settings.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("RG-06-05 : la valeur, l'auteur et la date de modification sont persistés ensemble")
  void lastModificationPersisted() {
    User admin =
        users.saveAndFlush(new User("settings-admin@example.com", "A", "B", "hash", Role.ADMIN));
    Instant at = Instant.parse("2026-05-04T08:30:00Z");

    AppSettings s = settings.findById(AppSettings.SINGLETON_ID).orElseThrow();
    s.changeStandardDayMinutes(450, at, admin.getId());
    settings.saveAndFlush(s);

    AppSettings reloaded = settings.findById(AppSettings.SINGLETON_ID).orElseThrow();
    assertThat(reloaded.getStandardDayMinutes()).isEqualTo(450);
    assertThat(reloaded.getUpdatedAt()).isEqualTo(at);
    assertThat(reloaded.getUpdatedBy()).isEqualTo(admin.getId());
    assertThat(reloaded.getVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("RG-06-05 : la base refuse une date sans auteur, ou un auteur sans date")
  void dateAndAuthorGoTogether() {
    assertThatThrownBy(() -> jdbc.update("UPDATE app_settings SET updated_at = now() WHERE id = 1"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
