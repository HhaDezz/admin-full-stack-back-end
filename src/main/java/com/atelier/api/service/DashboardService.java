package com.atelier.api.service;

import com.atelier.api.dto.response.AuditLogResponse;
import com.atelier.api.dto.response.DashboardSummaryResponse;
import com.atelier.api.dto.response.DashboardSummaryResponse.DaySignIns;
import com.atelier.api.dto.response.DashboardSummaryResponse.RoleCount;
import com.atelier.api.dto.response.UserResponse;
import com.atelier.api.entity.Role;
import com.atelier.api.repository.AuditLogRepository;
import com.atelier.api.repository.PermissionRepository;
import com.atelier.api.repository.RoleRepository;
import com.atelier.api.repository.UserRepository;
import com.atelier.api.security.CurrentUser;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class DashboardService {

    private static final int SIGN_IN_DAYS = 14;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final AuditLogRepository audit;

    public DashboardService(UserRepository users, RoleRepository roles, PermissionRepository permissions,
                            AuditLogRepository audit) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        long total = users.count();
        long active = users.countByEnabledTrue();
        LocalDate today = LocalDate.now();

        // Sign-ins per day from the audit log.
        LocalDate firstDay = today.minusDays(SIGN_IN_DAYS - 1);
        Map<LocalDate, long[]> byDay = new HashMap<>();
        for (Object[] row : audit.signInsPerDay(firstDay.atStartOfDay())) {
            LocalDate day = row[0] instanceof Date d ? d.toLocalDate() : LocalDate.parse(row[0].toString());
            long[] v = byDay.computeIfAbsent(day, k -> new long[2]);
            v["LOGIN".equals(row[1]) ? 0 : 1] += ((Number) row[2]).longValue();
        }
        List<DaySignIns> days = new ArrayList<>();
        long ok7 = 0;
        long failed7 = 0;
        for (int i = 0; i < SIGN_IN_DAYS; i++) {
            LocalDate day = firstDay.plusDays(i);
            long[] v = byDay.getOrDefault(day, new long[2]);
            days.add(new DaySignIns(day, v[0], v[1]));
            if (!day.isBefore(today.minusDays(6))) {
                ok7 += v[0];
                failed7 += v[1];
            }
        }

        boolean superAdmin = CurrentUser.isSuperAdmin();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : roles.countUsersPerRole()) counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        List<RoleCount> byRole = roles.findAll().stream()
                .filter(r -> superAdmin || !r.isSystem())
                .map((Role r) -> new RoleCount(r.getName(), counts.getOrDefault(r.getId(), 0L)))
                .filter(rc -> rc.count() > 0)
                .sorted(Comparator.comparingLong(RoleCount::count).reversed())
                .toList();

        List<UserResponse> recentUsers = hasAuthority("admin-user-view")
                ? users.findTop5ByOrderByCreatedAtDesc().stream().map(UserResponse::of).toList()
                : null;
        List<AuditLogResponse> activity = superAdmin
                ? AuditLogResponse.from(audit.recentActivity(LocalDateTime.now().minusDays(30), PageRequest.of(0, 8)))
                : null;

        return new DashboardSummaryResponse(total, active, total - active, users.countByCreatedAtAfter(LocalDateTime.now().minusDays(30)),
                roles.findAll().stream().filter(r -> superAdmin || !r.isSystem()).count(), permissions.count(),
                ok7, failed7, days, byRole, recentUsers, activity);
    }

    private static boolean hasAuthority(String name) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(name));
    }
}
