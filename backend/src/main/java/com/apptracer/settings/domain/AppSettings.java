package com.apptracer.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Paramètres de l'application (SFD-06 §2) : ligne unique, créée par la migration. N'étend pas
 * {@code AuditableEntity} : la dernière modification n'est écrite par le service que si la valeur
 * change (RG-06-05, RG-06-06). Jamais exposée directement par l'API.
 */
@Entity
@Table(name = "app_settings")
public class AppSettings {

  /** Identifiant de la ligne unique. */
  public static final short SINGLETON_ID = 1;

  @Id private Short id;

  // RG-06-02, RG-00-02
  @Column(name = "standard_day_minutes", nullable = false)
  private int standardDayMinutes;

  @Version
  @Column(nullable = false)
  private long version;

  // RG-06-05 : nuls tant que la valeur n'a jamais été modifiée
  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "updated_by")
  private Long updatedBy;

  protected AppSettings() {}

  public Short getId() {
    return id;
  }

  public int getStandardDayMinutes() {
    return standardDayMinutes;
  }

  public long getVersion() {
    return version;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Long getUpdatedBy() {
    return updatedBy;
  }

  /** RG-06-05 : change la valeur et trace qui et quand, ensemble. */
  public void changeStandardDayMinutes(int minutes, Instant at, long by) {
    this.standardDayMinutes = minutes;
    this.updatedAt = at;
    this.updatedBy = by;
  }
}
