package com.saicomex.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * SRS §36 — grants a user visibility of one project. No rows for a user
 * means unrestricted (group-wide) project visibility.
 */
@Entity
@Table(name = "user_project_access")
@Getter
@Setter
public class UserProjectAccess {

    @EmbeddedId
    private UserProjectAccessId id;
}
