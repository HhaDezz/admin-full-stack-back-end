package com.atelier.api.repository;

import com.atelier.api.entity.RevokedToken;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    @Modifying
    @Transactional
    @Query("DELETE FROM RevokedToken t WHERE t.expiresAt < :now")
    int deleteExpired(LocalDateTime now);
}
