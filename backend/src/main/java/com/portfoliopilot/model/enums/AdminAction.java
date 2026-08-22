package com.portfoliopilot.model.enums;

/** Auditable privileged actions. Mirrors {@code adminLogs.action}. */
public enum AdminAction {
    ADMIN_LOGIN,
    SUSPEND_USER,
    ACTIVATE_USER,
    SOFT_DELETE_USER,
    RESTORE_USER,
    PURGE_USER,
    CREATE_TEMPLATE,
    UPDATE_TEMPLATE,
    DEACTIVATE_TEMPLATE,
    DELETE_TEMPLATE,
    UNPUBLISH_PORTFOLIO,
    VIEW_USER_DETAIL
}
