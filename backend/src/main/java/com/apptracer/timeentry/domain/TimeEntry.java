package com.apptracer.timeentry.domain;

import com.apptracer.common.audit.AuditableEntity;
import com.apptracer.referential.domain.ActivityType;
import com.apptracer.referential.domain.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Saisie d'activité (SFD-01 §2). Jamais exposée directement par l'API. */
@Entity
@Table(name = "time_entries")
public class TimeEntry extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // RG-01-10 : compte propriétaire
  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 5)
  private Granularity granularity;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  // égale à startDate hors RANGE
  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  // RG-01-01 : renseignées en HOUR seulement
  @Column(name = "start_time")
  private LocalTime startTime;

  @Column(name = "end_time")
  private LocalTime endTime;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "activity_type_id", nullable = false)
  private ActivityType activityType;

  // RG-01-05 : cohérence avec le type contrôlée par le service
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id")
  private Project project;

  // RG-01-09
  @Column(length = 500)
  private String comment;

  // RG-01-08 : figées à l'enregistrement
  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Column(name = "standard_day_minutes", nullable = false)
  private int standardDayMinutes;

  @Version
  @Column(nullable = false)
  private long version;

  // RG-01-11 : suppression logique
  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deleted_by")
  private Long deletedBy;

  protected TimeEntry() {}

  public TimeEntry(Long userId, ActivityType activityType) {
    this.userId = userId;
    this.activityType = activityType;
  }

  /** Remplace la période ; les valeurs sont validées par le service (T-01-3) et par la base. */
  public void setPeriod(
      Granularity granularity,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      LocalTime endTime) {
    this.granularity = granularity;
    this.startDate = startDate;
    this.endDate = endDate;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  /** RG-01-08 : durée et journée standard figées ensemble. */
  public void setDuration(int durationMinutes, int standardDayMinutes) {
    this.durationMinutes = durationMinutes;
    this.standardDayMinutes = standardDayMinutes;
  }

  /** RG-01-11 : suppression logique, qui et quand ensemble. */
  public void markDeleted(Long deletedBy, Instant deletedAt) {
    this.deletedBy = deletedBy;
    this.deletedAt = deletedAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public Granularity getGranularity() {
    return granularity;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public ActivityType getActivityType() {
    return activityType;
  }

  public void setActivityType(ActivityType activityType) {
    this.activityType = activityType;
  }

  public Project getProject() {
    return project;
  }

  public void setProject(Project project) {
    this.project = project;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public int getStandardDayMinutes() {
    return standardDayMinutes;
  }

  public long getVersion() {
    return version;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public Long getDeletedBy() {
    return deletedBy;
  }
}
