package com.atelier.api.repository;

import com.atelier.api.entity.RecoveryCode;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    List<RecoveryCode> findByUserIdAndUsedAtIsNull(Long userId);

    long countByUserIdAndUsedAtIsNull(Long userId);

    @Modifying
    @Query("DELETE FROM RecoveryCode c WHERE c.userId = :userId")
    int deleteForUser(@Param("userId") Long userId);
}
