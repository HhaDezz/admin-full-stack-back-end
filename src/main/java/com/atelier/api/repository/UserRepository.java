package com.atelier.api.repository;

import com.atelier.api.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<User> findAllByOrderByCreatedAtDesc();

    List<User> findTop5ByOrderByCreatedAtDesc();

    long countByEnabledTrue();

    long countByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.roles r WHERE r.system = true")
    boolean existsSuperAdmin();
}
