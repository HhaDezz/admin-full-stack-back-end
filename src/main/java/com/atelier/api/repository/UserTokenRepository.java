package com.atelier.api.repository;

import com.atelier.api.entity.UserToken;
import com.atelier.api.enums.TokenPurpose;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    /** Retires earlier unused links when a new one is sent, so only the newest works. */
    @Modifying
    @Query("UPDATE UserToken t SET t.usedAt = :now WHERE t.user.id = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    int retireOpen(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM UserToken t WHERE t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
