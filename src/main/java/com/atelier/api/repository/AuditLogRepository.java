package com.atelier.api.repository;

import com.atelier.api.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Filtering on performed_at lets PostgreSQL skip whole monthly partitions.
     * An empty :category / :term means "no filter".
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.performedAt >= :start AND a.performedAt < :end
              AND (:category = '' OR a.category = :category)
              AND (:term = ''
                   OR LOWER(a.performedBy) LIKE :pattern
                   OR LOWER(a.entityName) LIKE :pattern
                   OR LOWER(COALESCE(a.requestPath, '')) LIKE :pattern
                   OR LOWER(COALESCE(a.details, '')) LIKE :pattern)
            ORDER BY a.performedAt DESC, a.id DESC
            """)
    List<AuditLog> search(@Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end,
                          @Param("category") String category,
                          @Param("term") String term,
                          @Param("pattern") String pattern,
                          Pageable pageable);

    /** [day, action, count] for sign-in events since the given time. */
    @Query(value = """
            SELECT CAST(performed_at AS date) AS day, action, COUNT(*)
            FROM audit.audit_events
            WHERE category = 'AUTH' AND action IN ('LOGIN', 'LOGIN_FAILED') AND performed_at >= :since
            GROUP BY 1, 2
            """, nativeQuery = true)
    List<Object[]> signInsPerDay(@Param("since") LocalDateTime since);

    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.performedAt >= :since AND a.category IN ('BUSINESS', 'AUTH')
            ORDER BY a.performedAt DESC, a.id DESC
            """)
    List<AuditLog> recentActivity(@Param("since") LocalDateTime since, Pageable pageable);

    /** A user's own sign-ins, security events and changes, newest first ("My activity"). */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE LOWER(a.performedBy) = :email AND a.category IN ('BUSINESS', 'AUTH')
              AND a.performedAt >= :since
            ORDER BY a.performedAt DESC, a.id DESC
            """)
    List<AuditLog> activityOf(@Param("email") String email, @Param("since") LocalDateTime since, Pageable pageable);
}
