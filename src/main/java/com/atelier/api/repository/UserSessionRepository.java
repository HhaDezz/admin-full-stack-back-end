package com.atelier.api.repository;

import com.atelier.api.entity.UserSession;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    @Query("""
            SELECT s FROM UserSession s
            WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now
            ORDER BY s.lastSeenAt DESC
            """)
    List<UserSession> findActive(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    boolean existsByUserIdAndUserAgent(Long userId, String userAgent);

    /** Signs out every device of a user except one (pass "" to sign out all; never bind NULL). */
    @Modifying
    @Query("""
            UPDATE UserSession s SET s.revokedAt = :now
            WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.id <> :keep
            """)
    int revokeAll(@Param("userId") Long userId, @Param("keep") String keep, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserSession s SET s.lastSeenAt = :now WHERE s.id = :id")
    int touch(@Param("id") String id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :cutoff OR s.revokedAt < :cutoff")
    int deleteOld(@Param("cutoff") LocalDateTime cutoff);
}
