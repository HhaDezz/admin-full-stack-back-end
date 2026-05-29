package com.atelier.api.entity;

import com.atelier.api.config.AuditDataChangeListener;
import com.atelier.api.config.AuditRequestInterceptor;
import com.atelier.api.enums.AuditAction;
import com.atelier.api.service.AuditLogService;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One audit record. Lives in its own schema (audit.audit_events), partitioned by month
 * (see V4__audit_schema.sql). The table is append-only: a database trigger rejects
 * UPDATE and DELETE, so never save() a row you loaded and never delete() one.
 */
@Getter
@Setter
@Entity
@Table(name = "audit_events", schema = "audit")
public class AuditLog {

    /** One row per API call that changes something. Written by AuditRequestInterceptor. */
    public static final String CATEGORY_REQUEST = "REQUEST";
    /** One row per database row inserted/updated/deleted. Written by AuditDataChangeListener. */
    public static final String CATEGORY_DATA = "DATA";
    /** A named event written on purpose by a service, via AuditLogService.logAction. */
    public static final String CATEGORY_BUSINESS = "BUSINESS";
    /** Sign-in, failed sign-in, sign-out. */
    public static final String CATEGORY_AUTH = "AUTH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category", nullable = false, length = 20)
    private String category = CATEGORY_BUSINESS;

    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private LocalDateTime performedAt = LocalDateTime.now();

    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    /** JSON: {"field": {"old": "...", "new": "..."}}. Only set on DATA rows. */
    @Column(name = "changes", columnDefinition = "TEXT")
    private String changes;
}
