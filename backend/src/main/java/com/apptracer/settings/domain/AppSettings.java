package com.apptracer.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Application settings (SFD-06 §2): a single row, created by the migration. Does not extend {@code
 * AuditableEntity}: the service writes the last change only when the value changes (RG-06-05,
 * RG-06-06). Never exposed directly by the API.
 */
@Entity
@Table(name = "app_settings")
public class AppSettings {

  /** Identifier of the single row. */
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

  /** RG-06-05: changes the value and records who and when, together. */
  public void changeStandardDayMinutes(int minutes, Instant at, long by) {
    this.standardDayMinutes = minutes;
    this.updatedAt = at;
    this.updatedBy = by;
  }
}
