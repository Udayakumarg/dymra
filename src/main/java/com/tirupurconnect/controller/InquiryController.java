package com.tirupurconnect.controller;

import com.tirupurconnect.dto.*;
import com.tirupurconnect.security.AuthContext;
import com.tirupurconnect.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;
    private final AuthContext    authContext;

    @PostMapping
    public ResponseEntity<ApiResponse<InquiryResponse>> createInquiry(
            @Valid @RequestBody InquiryCreateRequest req,
            @RequestHeader(value = "X-Tenant-ID", defaultValue = "tiruppur-zone1") String tenantId) {

        InquiryResponse response = inquiryService.createInquiry(
            req, authContext.requireBuyerId(), tenantId
        );
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }
}
