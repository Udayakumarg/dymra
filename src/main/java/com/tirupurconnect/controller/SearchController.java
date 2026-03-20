package com.tirupurconnect.controller;

import com.tirupurconnect.dto.ApiResponse;
import com.tirupurconnect.dto.SearchRequest;
import com.tirupurconnect.dto.SearchResponse;
import com.tirupurconnect.security.AppPrincipal;
import com.tirupurconnect.service.SearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @Valid @ModelAttribute SearchRequest req,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "tiruppur-zone1") String tenantId) {

        UUID buyerId = resolveOptionalBuyerId();
        SearchResponse response = searchService.search(req, tenantId, buyerId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private UUID resolveOptionalBuyerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppPrincipal principal) {
            return principal.getUserId();
        }
        return null;
    }
}
