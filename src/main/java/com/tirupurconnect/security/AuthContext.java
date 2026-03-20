package com.tirupurconnect.security;

import com.tirupurconnect.exception.ForbiddenException;
import com.tirupurconnect.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class AuthContext {

    public AppPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppPrincipal principal)) {
            throw new UnauthorizedException("Not authenticated");
        }
        return principal;
    }

    public UUID currentUserId()   { return current().getUserId(); }
    public String currentTenantId() { return current().getTenantId(); }

    public UUID requireBuyerId() {
        AppPrincipal p = current();
        if (!"BUYER".equals(p.getRole()) && !"ADMIN".equals(p.getRole()))
            throw new ForbiddenException("Buyer role required");
        return p.getUserId();
    }

    public UUID requireSupplierId() {
        AppPrincipal p = current();
        if (!"SUPPLIER".equals(p.getRole()) && !"ADMIN".equals(p.getRole()))
            throw new ForbiddenException("Supplier role required");
        return p.getUserId();
    }
}
