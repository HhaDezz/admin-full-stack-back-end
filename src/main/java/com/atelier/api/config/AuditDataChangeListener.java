package com.atelier.api.config;

import com.atelier.api.entity.AuditLog;
import com.atelier.api.entity.RevokedToken;
import com.atelier.api.enums.AuditAction;
import com.atelier.api.service.AuditContext;
import com.atelier.api.service.AuditWriter;

import jakarta.persistence.Entity;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Layer 3 of the audit log: one row for every database row that is inserted,
 * updated or deleted, with the old and new value of each field that changed.
 *
 * HOW IT WORKS
 *   Hibernate already knows exactly what changed — it has to, to build the
 *   UPDATE statement. It will tell anyone who registers an "event listener".
 *   This class is that listener, so it sees every entity in the system
 *   without a single line added to any service.
 *
 * WHY "POST COMMIT"
 *   Hibernate offers the same events at two moments: right after the SQL is
 *   sent, or after the transaction has committed. We listen after commit. A
 *   change that is rolled back never happened, and must not appear in the log.
 *
 * WHAT IS LEFT OUT
 *   - Entities listed in audit.data-changes.exclude (e.g. RevokedToken:
 *     constant churn, no audit value).
 *   - Fields whose name suggests a secret: the value is replaced with ***.
 *   - Timestamps Hibernate maintains by itself (updatedAt).
 *   - Collections. A changed list shows up as changes to its own rows.
 *
 * Turn the whole layer off with audit.data-changes.enabled=false.
 */
@Component
public class AuditDataChangeListener
        implements PostCommitInsertEventListener, PostCommitUpdateEventListener, PostCommitDeleteEventListener {

    private static final int MAX_VALUE_LENGTH = 300;
    private static final int MAX_CHANGES_LENGTH = 8000;
    private static final Set<String> NOISE_FIELDS = Set.of("updatedAt", "modifiedAt", "lastModifiedAt", "version", "lastLoginAt", "totpLastStep");

    private final AuditWriter auditWriter;
    private final boolean enabled;
    private final Set<String> excluded = new HashSet<>();

    public AuditDataChangeListener(
            AuditWriter auditWriter,
            @Value("${audit.data-changes.enabled:true}") boolean enabled,
            @Value("${audit.data-changes.exclude:RevokedToken,UserSession,UserToken,RecoveryCode,Notification}") String exclude) {
        this.auditWriter = auditWriter;
        this.enabled = enabled;
        // The audit table itself is always excluded, or every audit row would
        // produce another audit row, forever.
        this.excluded.add("AuditLog");
        Arrays.stream(exclude.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(this.excluded::add);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Tells Hibernate to call us after the commit rather than before it. */
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return true;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        try {
            Object entity = event.getEntity();
            if (skip(entity)) return;
            String[] names = event.getPersister().getPropertyNames();
            Object[] state = event.getState();
            StringBuilder json = new StringBuilder("{");
            for (int i = 0; i < names.length && i < state.length; i++) {
                if (NOISE_FIELDS.contains(names[i])) continue;
                String value = render(names[i], state[i]);
                if (value == null) continue; // empty fields add nothing on a new row
                appendChange(json, names[i], null, value);
            }
            json.append('}');
            write(entity, event.getId(), AuditAction.CREATE, "Created " + simpleName(entity), json.toString());
        } catch (Exception ignored) {
            // never let auditing break the application
        }
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        try {
            Object entity = event.getEntity();
            if (skip(entity)) return;
            String[] names = event.getPersister().getPropertyNames();
            Object[] oldState = event.getOldState();
            Object[] newState = event.getState();
            int[] dirty = event.getDirtyProperties();
            if (dirty == null || newState == null) return;

            StringBuilder json = new StringBuilder("{");
            StringBuilder fields = new StringBuilder();
            boolean softDeleted = false;
            for (int index : dirty) {
                if (index < 0 || index >= names.length) continue;
                String name = names[index];
                if (NOISE_FIELDS.contains(name)) continue;
                Object before = oldState != null && index < oldState.length ? oldState[index] : null;
                Object after = index < newState.length ? newState[index] : null;
                if (before instanceof Collection || after instanceof Collection
                        || before instanceof Map || after instanceof Map) continue;
                String oldValue = render(name, before);
                String newValue = render(name, after);
                if (oldValue == null && newValue == null) continue;
                if (oldValue != null && oldValue.equals(newValue)) continue;
                appendChange(json, name, oldValue, newValue);
                if (fields.length() > 0) fields.append(", ");
                fields.append(name);
                // Most tables here are "soft deleted": the row stays and a
                // deleted flag is set. To the user that IS a delete.
                if ("deleted".equals(name) && "true".equals(newValue)) softDeleted = true;
            }
            json.append('}');
            if (fields.length() == 0) return; // only noise fields changed

            AuditAction action = softDeleted ? AuditAction.DELETE : AuditAction.UPDATE;
            String details = softDeleted
                    ? "Deleted " + simpleName(entity)
                    : "Updated " + simpleName(entity) + ": " + fields;
            write(entity, event.getId(), action, details, json.toString());
        } catch (Exception ignored) {
            // never let auditing break the application
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        try {
            Object entity = event.getEntity();
            if (skip(entity)) return;
            write(entity, event.getId(), AuditAction.DELETE, "Deleted " + simpleName(entity) + " (row removed)", null);
        } catch (Exception ignored) {
            // never let auditing break the application
        }
    }

    // A commit that failed changed nothing, so there is nothing to record.
    @Override
    public void onPostInsertCommitFailed(PostInsertEvent event) {
    }

    @Override
    public void onPostUpdateCommitFailed(PostUpdateEvent event) {
    }

    @Override
    public void onPostDeleteCommitFailed(PostDeleteEvent event) {
    }

    // ---------------------------------------------------------------------

    private boolean skip(Object entity) {
        return !enabled || entity == null || excluded.contains(simpleName(entity));
    }

    private void write(Object entity, Object id, AuditAction action, String details, String changes) {
        AuditLog log = new AuditLog();
        log.setCategory(AuditLog.CATEGORY_DATA);
        log.setEntityName(AuditContext.truncate(simpleName(entity), 100));
        log.setEntityId(id instanceof Number number ? number.longValue() : null);
        log.setAction(action);
        // Read the user NOW, on the request thread. The background thread
        // that does the insert has no signed-in user.
        log.setPerformedBy(AuditContext.currentActor());
        log.setPerformedAt(LocalDateTime.now());
        log.setDetails(AuditContext.truncate(
                id == null || id instanceof Number ? details : details + " [id " + id + "]", 2000));
        if (changes != null && changes.length() > 2) {
            log.setChanges(changes.length() <= MAX_CHANGES_LENGTH
                    ? changes
                    : "{\"_truncated\":{\"new\":\"too many changes to store\"}}");
        }
        AuditContext.stampRequest(log);
        auditWriter.writeAsync(log);
    }

    private static String simpleName(Object entity) {
        Class<?> type = entity instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : entity.getClass();
        return type.getSimpleName();
    }

    /**
     * Turns a field value into short text, or null for "empty".
     * A link to another entity becomes "Customer#12" — never the whole object,
     * which could be huge, or lazy and no longer loadable after the commit.
     */
    private static String render(String field, Object value) {
        if (value == null) return null;
        String lower = field.toLowerCase();
        if (lower.contains("password") || lower.contains("secret") || lower.contains("token")) return "***";

        if (value instanceof HibernateProxy proxy) {
            return proxy.getHibernateLazyInitializer().getPersistentClass().getSimpleName()
                    + "#" + proxy.getHibernateLazyInitializer().getIdentifier();
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Enum || value instanceof Temporal || value instanceof UUID
                || value instanceof java.util.Date) {
            return AuditContext.truncate(value.toString(), MAX_VALUE_LENGTH);
        }
        if (value instanceof Collection || value instanceof Map || value.getClass().isArray()) return null;
        if (value.getClass().isAnnotationPresent(Entity.class)) {
            return value.getClass().getSimpleName() + "#" + idOf(value);
        }
        return "[" + value.getClass().getSimpleName() + "]";
    }

    private static Object idOf(Object entity) {
        try {
            return entity.getClass().getMethod("getId").invoke(entity);
        } catch (Exception e) {
            return "?";
        }
    }

    private static void appendChange(StringBuilder json, String field, String oldValue, String newValue) {
        if (json.length() > 1) json.append(',');
        json.append('"').append(escape(field)).append("\":{");
        boolean wrote = false;
        if (oldValue != null) {
            json.append("\"old\":\"").append(escape(oldValue)).append('"');
            wrote = true;
        }
        if (newValue != null) {
            if (wrote) json.append(',');
            json.append("\"new\":\"").append(escape(newValue)).append('"');
        }
        json.append('}');
    }

    /** Minimal JSON string escaping, so this class needs no JSON library. */
    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
