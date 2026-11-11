package com.apptracer.team.domain;

import com.apptracer.account.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Link between an account and a team. RG-05-04: the primary key is the account, so an account
 * belongs to at most one team. RG-05-14: who created the link, and when.
 */
@Entity
@Table(name = "team_members")
@EntityListeners(AuditingEntityListener.class)
public class TeamMember implements Persistable<Long> {

  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Column(name = "team_id", nullable = false, updatable = false)
  private Long teamId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "team_id", insertable = false, updatable = false)
  private Team team;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(name = "created_by", nullable = false, updatable = false)
  private Long createdBy;

  @Transient private boolean isNew = true;

  protected TeamMember() {}

  public TeamMember(Long userId, Long teamId) {
    this.userId = userId;
    this.teamId = teamId;
  }

  @Override
  public Long getId() {
    return userId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  public Long getUserId() {
    return userId;
  }

  public Long getTeamId() {
    return teamId;
  }

  public User getUser() {
    return user;
  }

  public Team getTeam() {
    return team;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Long getCreatedBy() {
    return createdBy;
  }
}
