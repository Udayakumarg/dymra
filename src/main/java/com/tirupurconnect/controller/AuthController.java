package com.tirupurconnect.controller;

import com.tirupurconnect.dto.*;
import com.tirupurconnect.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.ok(ApiResponse.ok("OTP sent to your WhatsApp", null));
    }

    @PostMapping("/otp/resend")
    public ResponseEntity<ApiResponse<Void>> resendOtp(@RequestParam String phone) {
        authService.sendOtp(phone);
        return ResponseEntity.ok(ApiResponse.ok("OTP resent", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        AuthResponse response = authService.login(req);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
