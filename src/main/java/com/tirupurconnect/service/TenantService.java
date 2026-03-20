package com.tirupurconnect.service;

import com.tirupurconnect.model.Tenant;
import com.tirupurconnect.repository.TenantRepository;
import com.tirupurconnect.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.MonthDay;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    public Tenant getBySlug(String slug) {
        return tenantRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + slug));
    }

    /**
     * Checks if today falls within any seasonal pause window defined in tenant config.
     * Pause format in JSONB: [{"name":"Pongal","start":"01-14","end":"01-17"}, ...]
     */
    @SuppressWarnings("unchecked")
    public boolean isSeasonalPauseActive(String tenantSlug) {
        Tenant tenant = getBySlug(tenantSlug);
        Object pauseConfig = tenant.getSeasonalPauses();
        if (!(pauseConfig instanceof List<?> pauses)) return false;

        MonthDay today = MonthDay.now();

        for (Object pause : pauses) {
            if (!(pause instanceof Map<?, ?> p)) continue;
            try {
                MonthDay start = MonthDay.parse("--" + p.get("start"));
                MonthDay end   = MonthDay.parse("--" + p.get("end"));
                if (!today.isBefore(start) && !today.isAfter(end)) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }
}
