package com.atelier.api.service;

import com.atelier.api.dto.request.create.RoleRequest;
import com.atelier.api.dto.response.RoleResponse;
import com.atelier.api.entity.Permission;
import com.atelier.api.entity.Role;
import com.atelier.api.exception.BusinessException;
import com.atelier.api.repository.PermissionRepository;
import com.atelier.api.repository.RoleRepository;
import com.atelier.api.security.CurrentUser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class RoleService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;

    public RoleService(RoleRepository roles, PermissionRepository permissions) {
        this.roles = roles;
        this.permissions = permissions;
    }

    /** System roles are only visible to super admins. */
    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        Map<Long, Long> counts = userCounts();
        boolean superAdmin = CurrentUser.isSuperAdmin();
        return roles.findAllByOrderByNameAsc().stream()
                .filter(r -> superAdmin || !r.isSystem())
                .map(r -> RoleResponse.of(r, counts.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(Long id) {
        Role role = find(id);
        if (role.isSystem() && !CurrentUser.isSuperAdmin()) throw BusinessException.notFound("Role");
        return RoleResponse.of(role, userCounts().getOrDefault(id, 0L));
    }

    @Transactional
    public RoleResponse create(RoleRequest req) {
        if (roles.existsByName(req.roleName())) throw BusinessException.conflict("A role with that name already exists.", "roleName");
        Role role = new Role();
        role.setName(req.roleName());
        role.setDescription(blankToNull(req.description()));
        if (req.permissions() != null) role.setPermissions(new HashSet<>(permissions.findByNameIn(req.permissions())));
        return RoleResponse.of(roles.save(role), 0L);
    }

    @Transactional
    public RoleResponse update(Long id, RoleRequest req) {
        Role role = editable(id);
        if (roles.existsByNameAndIdNot(req.roleName(), id)) {
            throw BusinessException.conflict("A role with that name already exists.", "roleName");
        }
        role.setName(req.roleName());
        role.setDescription(blankToNull(req.description()));
        if (req.permissions() != null) role.setPermissions(new HashSet<>(permissions.findByNameIn(req.permissions())));
        return RoleResponse.of(roles.save(role), userCounts().getOrDefault(id, 0L));
    }

    /** Replaces the role's permissions with the given permission ids. */
    @Transactional
    public RoleResponse assignPermissions(Long id, List<Long> permissionIds) {
        Role role = editable(id);
        List<Permission> found = permissionIds == null || permissionIds.isEmpty() ? List.of() : permissions.findByIdIn(permissionIds);
        role.setPermissions(new HashSet<>(found));
        return RoleResponse.of(roles.save(role), userCounts().getOrDefault(id, 0L));
    }

    @Transactional
    public void delete(Long id) {
        roles.delete(editable(id));
    }

    private Role find(Long id) {
        return roles.findById(id).orElseThrow(() -> BusinessException.notFound("Role"));
    }

    private Role editable(Long id) {
        Role role = find(id);
        if (role.isSystem()) throw BusinessException.forbidden("The " + role.getName() + " role is built in and can't be changed.");
        return role;
    }

    private Map<Long, Long> userCounts() {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : roles.countUsersPerRole()) map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
