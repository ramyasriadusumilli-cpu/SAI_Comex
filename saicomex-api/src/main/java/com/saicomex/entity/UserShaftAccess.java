package com.saicomex.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * SRS §36 — grants a user visibility of one shaft. No rows for a user
 * means unrestricted (group-wide) shaft visibility.
 */
@Entity
@Table(name = "user_shaft_access")
@Getter
@Setter
public class UserShaftAccess {

    @EmbeddedId
    private UserShaftAccessId id;
}
