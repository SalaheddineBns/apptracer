package com.apptracer.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * RG-00-04: traceability (who, when) of the creation and of the last change. {@code createdBy} /
 * {@code updatedBy} are null for rows created by the system.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private Long createdBy;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @LastModifiedBy
  @Column(name = "updated_by")
  private Long updatedBy;

  /**
   * Explicitly marks the entity as modified (RG-00-04) when the change concerns data outside the
   * entity, for example its links.
   */
  public void touch(Instant at, Long by) {
    this.updatedAt = at;
    this.updatedBy = by;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Long getUpdatedBy() {
    return updatedBy;
  }
}
