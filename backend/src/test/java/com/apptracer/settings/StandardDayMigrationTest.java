package com.apptracer.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Migration V8: moves the standard day from 7 h to 8 h without touching a value that was already
 * changed.
 */
@Testcontainers
class StandardDayMigrationTest {

  @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16");

  private JdbcTemplate freshDatabaseAt(String version) {
    DriverManagerDataSource ds =
        new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    Flyway.configure()
        .dataSource(ds)
        .cleanDisabled(false)
        .locations("classpath:db/migration")
        .load()
        .clean();
    migrate(ds, version);
    return new JdbcTemplate(ds);
  }

  private void migrate(DriverManagerDataSource ds, String target) {
    Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration")
        .target(target)
        .load()
        .migrate();
  }

  private DriverManagerDataSource dataSource() {
    return new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
  }

  @Test
  @DisplayName(
      "CA-06-01 : V8 passe une valeur d'installation jamais modifiée (420) à 480, sans trace")
  void neverModifiedValueBecomes480() {
    JdbcTemplate jdbc = freshDatabaseAt("7");
    assertThat(jdbc.queryForObject("SELECT standard_day_minutes FROM app_settings", Integer.class))
        .isEqualTo(420);

    migrate(dataSource(), "8");

    Map<String, Object> row = jdbc.queryForMap("SELECT * FROM app_settings WHERE id = 1");
    assertThat(row.get("standard_day_minutes")).isEqualTo(480); // RG-06-01
    assertThat(row.get("updated_at")).isNull(); // RG-06-05
    assertThat(row.get("updated_by")).isNull();
    assertThat(row.get("version")).isEqualTo(0L);
  }

  @Test
  @DisplayName(
      "RG-06-04 : V8 ne change pas une valeur déjà modifiée par un ADMIN (450, 420 ou 480)")
  void valueModifiedByAdminIsKept() {
    for (int chosen : new int[] {450, 420, 480}) {
      JdbcTemplate jdbc = freshDatabaseAt("7");
      jdbc.update(
          "INSERT INTO users (email, first_name, last_name, password_hash, role, created_at,"
              + " updated_at) VALUES ('admin@example.com', 'A', 'B', 'hash', 'ADMIN', now(), now())");
      Long adminId = jdbc.queryForObject("SELECT id FROM users", Long.class);
      jdbc.update(
          "UPDATE app_settings SET standard_day_minutes = ?, version = 1,"
              + " updated_at = '2026-05-04T08:30:00Z', updated_by = ? WHERE id = 1",
          chosen,
          adminId);

      migrate(dataSource(), "8");

      Map<String, Object> row = jdbc.queryForMap("SELECT * FROM app_settings WHERE id = 1");
      assertThat(row.get("standard_day_minutes")).as("valeur %d", chosen).isEqualTo(chosen);
      assertThat(row.get("version")).isEqualTo(1L);
      assertThat(row.get("updated_by")).isEqualTo(adminId);
      assertThat(row.get("updated_at")).isNotNull();
    }
  }
}
