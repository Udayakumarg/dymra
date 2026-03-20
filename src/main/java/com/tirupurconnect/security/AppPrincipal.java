package com.tirupurconnect.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class AppPrincipal {
    private final UUID   userId;
    private final String phone;
    private final String role;
    private final String tenantId;
}
