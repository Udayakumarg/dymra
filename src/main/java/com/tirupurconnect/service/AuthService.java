package com.tirupurconnect.service;

import com.tirupurconnect.dto.AuthResponse;
import com.tirupurconnect.dto.LoginRequest;
import com.tirupurconnect.dto.RegisterRequest;
import com.tirupurconnect.exception.BadRequestException;
import com.tirupurconnect.exception.ResourceNotFoundException;
import com.tirupurconnect.exception.UnauthorizedException;
import com.tirupurconnect.model.Tenant;
import com.tirupurconnect.model.User;
import com.tirupurconnect.repository.TenantRepository;
import com.tirupurconnect.repository.UserRepository;
import com.tirupurconnect.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository       userRepository;
    private final TenantRepository     tenantRepository;
    private final JwtService           jwtService;
    private final WhatsAppService      whatsAppService;
    private final StringRedisTemplate  redisTemplate;

    private static final String OTP_PREFIX      = "otp:";
    private static final Duration OTP_TTL       = Duration.ofMinutes(10);
    private static final String OTP_VERIFY_MAX  = "otp:attempts:";

    @Transactional
    public void register(RegisterRequest req) {
        Tenant tenant = tenantRepository.findBySlug(req.tenantSlug())
            .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + req.tenantSlug()));

        userRepository.findByTenantSlugAndPhone(req.tenantSlug(), req.phone())
            .ifPresent(u -> { throw new BadRequestException("Phone already registered"); });

        User user = new User();
        user.setTenant(tenant);
        user.setPhone(req.phone());
        user.setName(req.name());
        user.setRole(req.role());
        userRepository.save(user);

        sendOtp(req.phone());
        log.info("User registered: phone={} tenant={} role={}", req.phone(), req.tenantSlug(), req.role());
    }

    public void sendOtp(String phone) {
        String otp = String.format("%06d", new Random().nextInt(1_000_000));
        redisTemplate.opsForValue().set(OTP_PREFIX + phone, otp, OTP_TTL);
        whatsAppService.sendOtp(phone, otp);
        log.debug("OTP sent: phone={}", phone);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String storedOtp = redisTemplate.opsForValue().get(OTP_PREFIX + req.phone());
        if (storedOtp == null || !storedOtp.equals(req.otp())) {
            throw new UnauthorizedException("Invalid or expired OTP");
        }

        redisTemplate.delete(OTP_PREFIX + req.phone());

        User user = userRepository.findByPhone(req.phone())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isPhoneVerified()) {
            user.setPhoneVerified(true);
            userRepository.save(user);
        }

        String token = jwtService.generateToken(
            user.getId(), user.getPhone(),
            user.getRole().name(), user.getTenant().getSlug()
        );

        return new AuthResponse(token, user.getRole().name(), user.getId(), user.getTenant().getSlug());
    }
}
