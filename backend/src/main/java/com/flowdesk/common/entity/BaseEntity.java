package com.flowdesk.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base type for all JPA entities.
 *
 * <p>Provides an identity-strategy primary key plus {@code createdAt} /
 * {@code updatedAt} timestamps populated automatically by Spring Data JPA
 * auditing (see {@link com.flowdesk.config.JpaAuditingConfig}) rather than
 * being set manually in service code - this relies on Hibernate's dirty
 * checking to persist {@code updatedAt} whenever a managed entity's state
 * changes within a transaction.
 *
 * <p>{@code equals}/{@code hashCode} are based on the database identifier
 * once assigned, and fall back to reference equality for transient
 * (unsaved) entities, which is the safe default for JPA entities used in
 * collections (e.g. {@code Set<User>} members on {@link com.flowdesk.team.Team}).
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseEntity other)) {
            return false;
        }
        if (id == null || other.id == null) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
