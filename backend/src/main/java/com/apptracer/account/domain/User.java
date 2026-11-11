package com.apptracer.account.domain;

import com.apptracer.common.audit.AuditableEntity;
import com.apptracer.referential.domain.JobTitle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** User account (SFD-03 §2). Never exposed directly by the API. */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // RG-03-01 : unique sans tenir compte de la casse (index ux_users_email_lower)
  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "first_name", nullable = false, length = 100)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  private String lastName;

  // RG-03-03 : hachage BCrypt, jamais le mot de passe en clair
  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  // RG-03-10 : exactement un rôle
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Role role;

  @Column(nullable = false)
  private boolean active = true;

  // RG-09-02 : au plus une fonction, facultative, jamais pour un ADMIN ; RG-09-01 : sans effet sur
  // les droits
  @ManyToOne
  @JoinColumn(name = "job_title_id")
  private JobTitle jobTitle;

  @Version
  @Column(nullable = false)
  private long version;

  protected User() {}

  public User(String email, String firstName, String lastName, String passwordHash, Role role) {
    this.email = email;
    this.firstName = firstName;
    this.lastName = lastName;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public JobTitle getJobTitle() {
    return jobTitle;
  }

  public void setJobTitle(JobTitle jobTitle) {
    this.jobTitle = jobTitle;
  }

  public long getVersion() {
    return version;
  }
}
