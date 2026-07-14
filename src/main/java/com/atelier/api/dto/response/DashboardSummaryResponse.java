package com.atelier.api.dto.response;

import java.time.LocalDate;
import java.util.List;


public record DashboardSummaryResponse(
        long totalUsers,
        long activeUsers,
        long disabledUsers,
        long newUsersLast30Days,
        long totalRoles,
        long totalPermissions,
        long signInsLast7Days,
        long failedSignInsLast7Days,
        /* One entry per day for the last 14 days, oldest first. */
        List<DaySignIns> signInsByDay,
        List<RoleCount> usersByRole,
        /* Only for callers with admin-user-view; otherwise null. */
        List<UserResponse> recentUsers,
        /* Only for super admins; otherwise null. */
        List<AuditLogResponse> recentActivity) {

    public record DaySignIns(LocalDate date, long success, long failed) {
    }

    public record RoleCount(String role, long count) {
    }
}
