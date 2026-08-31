package com.saicomex.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * SRS §39: "Records should not be physically deleted where financial/audit
 * integrity would be affected. Use: soft deletion + audit trail."
 *
 * <p>Every repository query in this application filters {@code deletedAt IS
 * NULL} explicitly rather than relying on a Hibernate {@code @Where} clause.
 * That is deliberate — {@code @Where} is silently ignored by native queries
 * and by {@code JOIN FETCH} on the inverse side, which is exactly how deleted
 * rows leak back into a financial total.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 160)
    private String deletedBy;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete(String actor) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = actor;
    }
}
