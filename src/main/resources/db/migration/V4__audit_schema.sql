-- =============================================================================
-- Audit log, in its own schema and partitioned by month.
--
-- WHY A SEPARATE SCHEMA
--   Audit data is append-only, grows without limit and is thrown away on a
--   schedule. Keeping it in `audit.*` separates it from `public.*` for
--   permissions, backups and housekeeping, while it stays in the same database.
--
-- WHY PARTITIONS
--   Retention is "keep N months" (audit.retention-months). With one partition per
--   month, removing a month is DROP TABLE: instant, and the space comes back.
--
-- The application calls audit.ensure_partition / audit.drop_old_partitions at
-- startup and every night (AuditMaintenanceService).
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS audit;

-- A sequence + DEFAULT rather than IDENTITY: identity columns on partitioned
-- tables need PostgreSQL 17.
CREATE SEQUENCE audit.audit_events_id_seq;

CREATE TABLE audit.audit_events (
    id            BIGINT       NOT NULL DEFAULT nextval('audit.audit_events_id_seq'),
    performed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- REQUEST | DATA | BUSINESS | AUTH
    category      VARCHAR(20)  NOT NULL DEFAULT 'BUSINESS',
    action        VARCHAR(50)  NOT NULL,
    entity_name   VARCHAR(100) NOT NULL,
    entity_id     BIGINT,
    performed_by  VARCHAR(255) NOT NULL,
    -- request_id is shared by every row produced by one API call.
    request_id    VARCHAR(36),
    ip_address    VARCHAR(64),
    user_agent    VARCHAR(300),
    http_method   VARCHAR(10),
    request_path  VARCHAR(500),
    status_code   INT,
    duration_ms   BIGINT,
    details       TEXT,
    -- JSON: {"field": {"old": "...", "new": "..."}} (DATA rows only)
    changes       TEXT,
    PRIMARY KEY (id, performed_at)
) PARTITION BY RANGE (performed_at);

ALTER SEQUENCE audit.audit_events_id_seq OWNED BY audit.audit_events.id;

CREATE INDEX idx_audit_events_performed_at ON audit.audit_events (performed_at DESC);
CREATE INDEX idx_audit_events_actor        ON audit.audit_events (performed_by, performed_at DESC);
CREATE INDEX idx_audit_events_entity       ON audit.audit_events (entity_name, entity_id);
CREATE INDEX idx_audit_events_request      ON audit.audit_events (request_id);
CREATE INDEX idx_audit_events_category     ON audit.audit_events (category, performed_at DESC);

-- Creates the partition for the month containing p_month if missing. TRUE when created.
CREATE OR REPLACE FUNCTION audit.ensure_partition(p_month DATE) RETURNS BOOLEAN AS $$
DECLARE
    v_start DATE := date_trunc('month', p_month)::date;
    v_end   DATE := (date_trunc('month', p_month) + INTERVAL '1 month')::date;
    v_name  TEXT := 'audit_events_' || to_char(v_start, 'YYYY_MM');
BEGIN
    IF to_regclass('audit.' || v_name) IS NOT NULL THEN
        RETURN FALSE;
    END IF;
    EXECUTE format(
        'CREATE TABLE audit.%I PARTITION OF audit.audit_events FOR VALUES FROM (%L) TO (%L)',
        v_name, v_start, v_end);
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- Drops monthly partitions older than p_keep_months full months. Returns how many.
CREATE OR REPLACE FUNCTION audit.drop_old_partitions(p_keep_months INT) RETURNS INT AS $$
DECLARE
    v_cutoff DATE := (date_trunc('month', CURRENT_DATE) - make_interval(months => p_keep_months))::date;
    v_month  DATE;
    v_count  INT := 0;
    r        RECORD;
BEGIN
    FOR r IN
        SELECT c.relname
        FROM pg_inherits i
        JOIN pg_class c     ON c.oid = i.inhrelid
        JOIN pg_class p     ON p.oid = i.inhparent
        JOIN pg_namespace n ON n.oid = p.relnamespace
        WHERE n.nspname = 'audit'
          AND p.relname = 'audit_events'
          AND c.relname ~ '^audit_events_[0-9]{4}_[0-9]{2}$'
    LOOP
        v_month := to_date(right(r.relname, 7), 'YYYY_MM');
        IF v_month < v_cutoff THEN
            EXECUTE format('DROP TABLE audit.%I', r.relname);
            v_count := v_count + 1;
        END IF;
    END LOOP;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- Append-only: rows can't be changed or deleted one at a time.
CREATE OR REPLACE FUNCTION audit.reject_change() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit.audit_events is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_append_only
    AFTER UPDATE OR DELETE ON audit.audit_events
    FOR EACH ROW EXECUTE FUNCTION audit.reject_change();

DO $$
DECLARE
    m INT;
BEGIN
    FOR m IN 0..2 LOOP
        PERFORM audit.ensure_partition((date_trunc('month', CURRENT_DATE) + make_interval(months => m))::date);
    END LOOP;
END $$;
