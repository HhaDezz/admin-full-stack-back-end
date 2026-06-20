package com.atelier.api.controller;

import com.atelier.api.dto.response.ApiResponse;
import com.atelier.api.dto.response.AuditLogResponse;
import com.atelier.api.service.AuditLogService;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/audit-logs")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    /**
     * Super admins only. Deliberately not an ordinary permission: permissions can be
     * ticked onto any role, and the people being audited must not be able to grant
     * themselves the audit log.
     *
     * Filters (all optional): date_from / date_to (yyyy-MM-dd, inclusive),
     * category (REQUEST | DATA | BUSINESS | AUTH), search, limit (default 2000, max 5000).
     */
    @PreAuthorize("hasAuthority('SYSTEM_USER')")
    @GetMapping
    public ApiResponse<List<AuditLogResponse>> search(
            @RequestParam(name = "date_from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "date_to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(AuditLogResponse.from(service.search(from, to, category, search, limit)));
    }
}
