package com.tirupurconnect.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Configuration
public class WebConfig {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Split comma-separated origins from env var
        List<String> origins = List.of(allowedOrigins.split(","));
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Bean(name = "mdcRequestContextFilter")
    public MdcRequestContextFilter mdcRequestContextFilter() {
        return new MdcRequestContextFilter();
    }

    public static class MdcRequestContextFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String requestId = request.getHeader("X-Request-ID");
            if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();

            String tenantId = request.getHeader("X-Tenant-ID");
            if (tenantId == null || tenantId.isBlank()) tenantId = "tiruppur-zone1";

            MDC.put("requestId", requestId);
            MDC.put("tenantId",  tenantId);
            response.setHeader("X-Request-ID", requestId);

            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove("requestId");
                MDC.remove("tenantId");
            }
        }
    }
}
