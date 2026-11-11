package com.apptracer.team.domain;

import com.apptracer.account.domain.User;
import com.apptracer.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** Team (SFD-05 §2). Never exposed directly by the API. */
@Entity
@Table(name = "teams")
public class Team extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // RG-05-01: unique, case-insensitive (index ux_teams_name_lower)
  @Column(nullable = false, length = 100)
  private String name;

  // RG-05-02: exactly one manager; RG-05-06: several teams possible
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "manager_id", nullable = false)
  private User manager;

  @Version
  @Column(nullable = false)
  private long version;

  // RG-05-09, RG-05-14: deletion recorded
  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deleted_by")
  private Long deletedBy;

  protected Team() {}

  public Team(String name, User manager) {
    this.name = name;
    this.manager = manager;
  }

  /** RG-05-09, RG-05-14: permanent deletion, who and when together. */
  public void markDeleted(Long deletedBy, Instant deletedAt) {
    this.deletedBy = deletedBy;
    this.deletedAt = deletedAt;
    touch(deletedAt, deletedBy);
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public User getManager() {
    return manager;
  }

  public void setManager(User manager) {
    this.manager = manager;
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
