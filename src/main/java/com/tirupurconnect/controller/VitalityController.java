package com.tirupurconnect.controller;

import com.tirupurconnect.dto.ApiResponse;
import com.tirupurconnect.dto.VitalityStatusResponse;
import com.tirupurconnect.exception.ResourceNotFoundException;
import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.repository.SupplierRepository;
import com.tirupurconnect.security.AuthContext;
import com.tirupurconnect.service.VitalityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vitality")
@RequiredArgsConstructor
public class VitalityController {

    private final VitalityService    vitalityService;
    private final SupplierRepository supplierRepository;
    private final AuthContext        authContext;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('SUPPLIER', 'ADMIN')")
    public ResponseEntity<ApiResponse<VitalityStatusResponse>> myStatus() {
        UUID userId = authContext.requireSupplierId();
        Supplier supplier = supplierRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Supplier profile not found"));

        VitalityStatusResponse response = new VitalityStatusResponse(
            supplier.getId(),
            supplier.getVitalityScore(),
            supplier.getStatus().name(),
            supplier.getLastActiveAt()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // FIX #23: Admin-only endpoint now protected with @PreAuthorize
    @PostMapping("/admin/recompute/{supplierId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> adminRecompute(@PathVariable UUID supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
            .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + supplierId));
        vitalityService.recomputeScore(supplier);
        return ResponseEntity.ok(ApiResponse.ok("Recompute triggered", null));
    }
}
