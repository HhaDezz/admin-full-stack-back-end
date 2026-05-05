-- Signed-out tokens (SHA-256 hashes), kept until they would have expired.
CREATE TABLE revoked_tokens (
    token_hash VARCHAR(64)  PRIMARY KEY,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_revoked_tokens_expires ON revoked_tokens (expires_at);
