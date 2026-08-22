package com.portfoliopilot.model.enums;

/**
 * Authorization role. Mirrors the {@code users.role} enum in
 * {@code mongodb/schemas/users.schema.json} - the validator only accepts these
 * two strings, so persisting the enum NAME (Spring Data's default) is required.
 * Never switch to ordinals.
 */
public enum Role {
    USER,
    ADMIN;

    /** Spring Security expects the {@code ROLE_} prefix on granted authorities. */
    public String authority() {
        return "ROLE_" + name();
    }
}
