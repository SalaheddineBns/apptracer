package com.apptracer.referential.domain;

import com.apptracer.common.audit.AuditableEntity;
import com.apptracer.team.domain.Team;
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

/** Project (SFD-02 §2). Never exposed directly by the API. */
@Entity
@Table(name = "projects")
public class Project extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // RG-02-01: unique, cannot change after creation (no setter)
  @Column(nullable = false, updatable = false, length = 30)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  // RG-02-05: never deleted, only deactivated
  @Column(nullable = false)
  private boolean active = true;

  /** RG-10-01: at most one team, optional (shared project); loaded with the project. */
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "team_id")
  private Team team;

  @Version
  @Column(nullable = false)
  private long version;

  protected Project() {}

  public Project(String code, String name) {
    this.code = code;
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Team getTeam() {
    return team;
  }

  public void setTeam(Team team) {
    this.team = team;
  }

  public long getVersion() {
    return version;
  }
}
