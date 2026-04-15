-- Built-in permissions and roles.
-- To add a permission for your own feature, create a new migration (V5__..., V6__...) that
-- INSERTs it here and, if you like, grants it to ADMIN. Super admins get it automatically.

INSERT INTO permissions (name, description) VALUES
    ('dashboard-view',    'See the dashboard'),
    ('admin-user-view',   'See the list of users'),
    ('admin-user-create', 'Add users'),
    ('admin-user-edit',   'Edit users, enable and disable accounts'),
    ('admin-user-delete', 'Delete users'),
    ('admin-role-view',   'See roles and the permission catalogue'),
    ('admin-role-create', 'Create roles'),
    ('admin-role-edit',   'Rename roles and change their permissions'),
    ('admin-role-delete', 'Delete roles');

INSERT INTO roles (name, description, is_system) VALUES
    ('SUPER_ADMIN', 'Built-in. Holds every permission, including ones added later, and can see the audit log.', TRUE),
    ('ADMIN',       'Manages users and roles.', FALSE),
    ('USER',        'Standard member. Can see the dashboard.', FALSE);

-- ADMIN: everything that exists today.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ADMIN';

-- SUPER_ADMIN bypasses checks in code; listing everything keeps the Roles screen accurate.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'SUPER_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'dashboard-view' WHERE r.name = 'USER';
