package com.apptracer.referential.domain;

import com.apptracer.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Activity type (SFD-02 §2). Never exposed directly by the API. */
@Entity
@Table(name = "activity_types")
public class ActivityType extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // RG-02-01 : unique, immuable après création (pas de setter)
  @Column(nullable = false, updatable = false, length = 30)
  private String code;

  @Column(nullable = false, length = 100)
  private String label;

  // RG-02-03
  @Column(name = "project_required", nullable = false)
  private boolean projectRequired;

  // RG-02-04
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private TagColor color;

  // RG-02-05 : jamais supprimé, seulement désactivé
  @Column(nullable = false)
  private boolean active = true;

  @Version
  @Column(nullable = false)
  private long version;

  protected ActivityType() {}

  public ActivityType(String code, String label, boolean projectRequired, TagColor color) {
    this.code = code;
    this.label = label;
    this.projectRequired = projectRequired;
    this.color = color;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public boolean isProjectRequired() {
    return projectRequired;
  }

  public void setProjectRequired(boolean projectRequired) {
    this.projectRequired = projectRequired;
  }

  public TagColor getColor() {
    return color;
  }

  public void setColor(TagColor color) {
    this.color = color;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public long getVersion() {
    return version;
  }
}
