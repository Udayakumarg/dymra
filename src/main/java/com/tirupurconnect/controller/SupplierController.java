package com.tirupurconnect.controller;

import com.tirupurconnect.dto.*;
import com.tirupurconnect.security.AuthContext;
import com.tirupurconnect.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final AuthContext     authContext;

    @PostMapping("/me")
    public ResponseEntity<ApiResponse<SupplierResponse>> createProfile(
            @Valid @RequestBody SupplierCreateRequest req,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "tiruppur-zone1") String tenantId) {

        SupplierResponse response = supplierService.createProfile(
            req, authContext.requireSupplierId(), tenantId
        );
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SupplierResponse>> getProfile() {
        SupplierResponse response = supplierService.getProfile(authContext.currentUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateProfile(
            @Valid @RequestBody SupplierCreateRequest req,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "tiruppur-zone1") String tenantId) {

        SupplierResponse response = supplierService.updateProfile(
            req, authContext.requireSupplierId(), tenantId
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
