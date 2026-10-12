package com.apptracer.referential.domain;

import com.apptracer.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Projet (SFD-02 §2). Jamais exposé directement par l'API. */
@Entity
@Table(name = "projects")
public class Project extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // RG-02-01 : unique, immuable après création (pas de setter)
  @Column(nullable = false, updatable = false, length = 30)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  // RG-02-05 : jamais supprimé, seulement désactivé
  @Column(nullable = false)
  private boolean active = true;

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

  public long getVersion() {
    return version;
  }
}
