package com.atelier.api.repository;

import com.atelier.api.entity.Permission;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByNameAsc();

    List<Permission> findByIdIn(Collection<Long> ids);

    List<Permission> findByNameIn(Collection<String> names);
}
