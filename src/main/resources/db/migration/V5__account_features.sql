-- Self sign-up with email verification, password reset, two-step sign-in,
-- active sessions and in-app notifications.

-- Users created before this release (or by an administrator) count as verified.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP;
UPDATE users SET email_verified_at = created_at;

-- Two-step sign-in (authenticator app). The secret is stored encrypted (AES-GCM).
-- totp_enabled_at stays NULL while setup is pending.
ALTER TABLE users ADD COLUMN totp_secret VARCHAR(255);
ALTER TABLE users ADD COLUMN totp_enabled_at TIMESTAMP;
-- The last 30-second time step a code was accepted for, so a code can't be used twice.
ALTER TABLE users ADD COLUMN totp_last_step BIGINT;

-- Notification preferences as JSON, e.g. {"security":{"inApp":true,"email":true}, ...}
ALTER TABLE users ADD COLUMN notification_settings TEXT;

-- One-time links sent by email: VERIFY_EMAIL and RESET_PASSWORD. Only a SHA-256 hash is stored.
CREATE TABLE user_tokens (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose     VARCHAR(30) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMP   NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_tokens_user ON user_tokens (user_id, purpose);

-- Single-use recovery codes for two-step sign-in (hashed).
CREATE TABLE recovery_codes (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash   VARCHAR(64) NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_recovery_codes_user ON recovery_codes (user_id);

-- One row per signed-in device. The access token carries the id (jti).
CREATE TABLE user_sessions (
    id            VARCHAR(36)  PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ip_address    VARCHAR(64),
    user_agent    VARCHAR(400),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at    TIMESTAMP    NOT NULL,
    revoked_at    TIMESTAMP
);
CREATE INDEX idx_user_sessions_user ON user_sessions (user_id, last_seen_at DESC);

CREATE TABLE notifications (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category    VARCHAR(20)  NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        VARCHAR(1000),
    link        VARCHAR(255),
    read_at     TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
