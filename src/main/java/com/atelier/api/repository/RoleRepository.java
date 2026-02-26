package com.atelier.api.repository;

import com.atelier.api.entity.Role;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<Role> findAllByOrderByNameAsc();

    /** [roleId, userCount] for every role that has at least one user. */
    @Query(value = "SELECT role_id, COUNT(*) FROM user_roles GROUP BY role_id", nativeQuery = true)
    List<Object[]> countUsersPerRole();
}
