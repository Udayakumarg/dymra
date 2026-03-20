package com.tirupurconnect.service;

import com.tirupurconnect.config.AppProperties;
import com.tirupurconnect.event.OutboxEventPublisher;
import com.tirupurconnect.event.VitalityScoreUpdatedEvent;
import com.tirupurconnect.exception.ResourceNotFoundException;
import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.model.Supplier.SupplierStatus;
import com.tirupurconnect.model.VitalityEvent;
import com.tirupurconnect.model.VitalityEvent.VitalitySignal;
import com.tirupurconnect.repository.SupplierRepository;
import com.tirupurconnect.repository.VitalityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VitalityService {

    private final SupplierRepository      supplierRepository;
    private final VitalityEventRepository vitalityEventRepository;
    private final OutboxEventPublisher    eventPublisher;
    private final AppProperties           props;
    private final TenantService           tenantService;

    /**
     * Records a vitality signal immediately.
     * Called by consumers — runs in its own transaction.
     */
    @Transactional
    public void recordSignal(UUID supplierId, VitalitySignal signal, short points) {
        VitalityEvent event = new VitalityEvent(supplierId, signal, points);
        vitalityEventRepository.save(event);

        supplierRepository.findById(supplierId).ifPresent(s -> {
            s.setLastActiveAt(Instant.now());
            supplierRepository.save(s);
        });

        log.debug("Vitality signal recorded: supplier={} signal={} points={}", supplierId, signal, points);
    }

    /**
     * Batch recompute — every Sunday 2 AM (cron: "0 0 2 * * SUN").
     * Processes all non-closed suppliers across all tenants.
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    @Transactional
    public void batchRecompute() {
        log.info("Vitality batch recompute started");
        List<Supplier> suppliers = supplierRepository.findAll().stream()
            .filter(s -> s.getStatus() != SupplierStatus.CLOSED)
            .toList();

        suppliers.forEach(s -> {
            try {
                recomputeScore(s);
            } catch (Exception e) {
                log.error("Vitality recompute failed for supplier={}: {}", s.getId(), e.getMessage());
            }
        });

        log.info("Vitality batch recompute complete: {} suppliers processed", suppliers.size());
    }

    @Transactional
    public void recomputeScore(Supplier supplier) {
        String tenantSlug = supplier.getTenant().getSlug();

        if (tenantService.isSeasonalPauseActive(tenantSlug)) {
            log.debug("Seasonal pause active, skipping: supplier={} tenant={}", supplier.getId(), tenantSlug);
            return;
        }

        int windowDays = props.getVitality().getWindowDays();
        Instant windowStart = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        int totalPoints = vitalityEventRepository.sumPointsSince(supplier.getId(), windowStart);
        short newScore  = (short) Math.min(100, Math.max(0, totalPoints));
        SupplierStatus newStatus = resolveStatus(newScore);

        short oldScore        = supplier.getVitalityScore();
        SupplierStatus oldStatus = supplier.getStatus();

        supplierRepository.updateVitalityScoreAndStatus(supplier.getId(), newScore, newStatus);

        VitalityScoreUpdatedEvent event = VitalityScoreUpdatedEvent.of(
            supplier.getId(), tenantSlug,
            oldScore, newScore,
            oldStatus.name(), newStatus.name(),
            "BATCH_RECOMPUTE", (short) 0
        );
        eventPublisher.saveToOutbox(
            supplier.getId().toString(),
            VitalityScoreUpdatedEvent.TYPE,
            tenantSlug,
            event
        );

        log.info("Vitality recomputed: supplier={} {}->{}  {}->{}",
            supplier.getId(), oldScore, newScore, oldStatus, newStatus);
    }

    private SupplierStatus resolveStatus(short score) {
        AppProperties.Vitality.Thresholds t = props.getVitality().getThresholds();
        if (score >= t.getActive())  return SupplierStatus.ACTIVE;
        if (score >= t.getDormant()) return SupplierStatus.DORMANT;
        if (score >= t.getFading())  return SupplierStatus.FADING;
        return SupplierStatus.GHOST;
    }
}
