package com.atelier.api.dto.response;

import com.atelier.api.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.List;

public record AuditLogResponse(
        Long id,
        String category,
        String action,
        String module,
        Long recordId,
        String userName,
        LocalDateTime createdAt,
        String details,
        /* JSON text: {"field": {"old": "...", "new": "..."}}. Only on DATA rows. */
        String changes,
        String requestId,
        String ipAddress,
        String userAgent,
        String httpMethod,
        String path,
        Integer statusCode,
        Long durationMs) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getCategory(),
                log.getAction() == null ? null : log.getAction().name(), log.getEntityName(), log.getEntityId(),
                log.getPerformedBy(), log.getPerformedAt(), log.getDetails(), log.getChanges(), log.getRequestId(),
                log.getIpAddress(), log.getUserAgent(), log.getHttpMethod(), log.getRequestPath(), log.getStatusCode(),
                log.getDurationMs());
    }

    public static List<AuditLogResponse> from(List<AuditLog> logs) {
        return logs.stream().map(AuditLogResponse::from).toList();
    }
}
