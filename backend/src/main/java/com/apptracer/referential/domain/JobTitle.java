package com.apptracer.referential.domain;

import com.apptracer.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Fonction d'un collaborateur (SFD-09 §2). Jamais exposée directement par l'API. */
@Entity
@Table(name = "job_titles")
public class JobTitle extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // RG-09-05 : unique (insensible à la casse), immuable après création (pas de setter)
  @Column(nullable = false, updatable = false, length = 30)
  private String code;

  @Column(nullable = false, length = 100)
  private String label;

  // RG-09-06 : jamais supprimée, seulement désactivée
  @Column(nullable = false)
  private boolean active = true;

  @Version
  @Column(nullable = false)
  private long version;

  protected JobTitle() {}

  public JobTitle(String code, String label) {
    this.code = code;
    this.label = label;
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
