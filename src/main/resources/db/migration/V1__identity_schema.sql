-- Users, roles and permissions.

CREATE TABLE permissions (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE roles (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL UNIQUE,
    description VARCHAR(255),
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id                     BIGSERIAL    PRIMARY KEY,
    name                   VARCHAR(120) NOT NULL,
    email                  VARCHAR(255) NOT NULL UNIQUE,
    phone                  VARCHAR(40),
    address                VARCHAR(255),
    password_hash          VARCHAR(100) NOT NULL,
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    is_system              BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at          TIMESTAMP,
    credentials_changed_at TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_created_at ON users (created_at DESC);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role ON user_roles (role_id);
