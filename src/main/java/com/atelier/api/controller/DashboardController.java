package com.atelier.api.controller;

import com.atelier.api.dto.response.ApiResponse;
import com.atelier.api.dto.response.DashboardSummaryResponse;
import com.atelier.api.service.DashboardService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('dashboard-view')")
    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary() {
        return ApiResponse.ok(service.summary());
    }
}
