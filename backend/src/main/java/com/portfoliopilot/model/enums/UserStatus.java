package com.portfoliopilot.model.enums;

/**
 * Account lifecycle state. Mirrors {@code users.status}.
 *
 * <ul>
 *   <li>{@code ACTIVE}    - may authenticate</li>
 *   <li>{@code SUSPENDED} - blocked by an admin, data retained</li>
 *   <li>{@code DELETED}   - soft-deleted; mirrors {@code users.deleted = true}</li>
 * </ul>
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
