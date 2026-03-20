package com.tirupurconnect.controller;

import com.tirupurconnect.dto.*;
import com.tirupurconnect.security.AuthContext;
import com.tirupurconnect.service.ListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final AuthContext    authContext;

    @PostMapping
    public ResponseEntity<ApiResponse<ListingResponse>> create(
            @Valid @RequestBody ListingCreateRequest req,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "tiruppur-zone1") String tenantId) {

        ListingResponse response = listingService.create(
            req, authContext.requireSupplierId(), tenantId
        );
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @GetMapping("/supplier/{supplierId}")
    public ResponseEntity<ApiResponse<List<ListingResponse>>> getBySupplier(
            @PathVariable UUID supplierId) {

        return ResponseEntity.ok(ApiResponse.ok(listingService.getBySupplier(supplierId)));
    }

    @PutMapping("/{listingId}")
    public ResponseEntity<ApiResponse<ListingResponse>> update(
            @PathVariable UUID listingId,
            @Valid @RequestBody ListingCreateRequest req,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "tiruppur-zone1") String tenantId) {

        ListingResponse response = listingService.update(
            listingId, req, authContext.requireSupplierId(), tenantId
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{listingId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID listingId) {
        listingService.delete(listingId, authContext.currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Listing deactivated", null));
    }
}
