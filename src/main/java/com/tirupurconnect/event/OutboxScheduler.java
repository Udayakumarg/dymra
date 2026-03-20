package com.tirupurconnect.event;

import com.tirupurconnect.config.AppProperties;
import com.tirupurconnect.model.OutboxEvent;
import com.tirupurconnect.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxEventRepository outboxRepository;
    private final OutboxEventPublisher  publisher;
    private final AppProperties         props;

    // FIX #22: NOT @Transactional at the batch level.
    // Each event is processed in its own transaction so a failure on one
    // doesn't roll back saves for others. Redis publish is non-transactional anyway.
    @Scheduled(fixedDelayString = "${app.outbox.scheduler-delay-ms:5000}")
    public void processOutbox() {
        List<OutboxEvent> pending = outboxRepository.findPendingEvents();
        if (!pending.isEmpty()) {
            log.debug("Processing {} pending outbox events", pending.size());
            pending.forEach(this::processSingle);
        }

        int maxRetries = props.getOutbox().getMaxRetryAttempts();
        List<OutboxEvent> retryable = outboxRepository.findRetryableEvents(maxRetries);
        if (!retryable.isEmpty()) {
            log.debug("Retrying {} failed outbox events", retryable.size());
            retryable.forEach(this::processSingle);
        }
    }

    // Each event gets its own transaction — success or failure is isolated
    @Transactional
    public void processSingle(OutboxEvent event) {
        // Re-fetch to ensure we have the latest state and a fresh transaction
        OutboxEvent fresh = outboxRepository.findById(event.getId()).orElse(null);
        if (fresh == null || fresh.getStatus() == OutboxEvent.OutboxStatus.PROCESSED) {
            return; // Already handled by another instance / thread
        }

        boolean published = publisher.publishToStream(fresh);

        if (published) {
            fresh.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
            fresh.setProcessedAt(Instant.now());
            outboxRepository.save(fresh);
            log.debug("Outbox event processed: eventId={} type={}", fresh.getEventId(), fresh.getEventType());
        } else {
            short retries = (short) (fresh.getRetryCount() + 1);
            fresh.setRetryCount(retries);

            int maxRetries = props.getOutbox().getMaxRetryAttempts();
            if (retries >= maxRetries) {
                fresh.setStatus(OutboxEvent.OutboxStatus.FAILED);
                fresh.setLastError("Max retry attempts reached (" + maxRetries + ")");
                log.error("Outbox event permanently failed: eventId={} type={}", fresh.getEventId(), fresh.getEventType());
            } else {
                fresh.setLastError("Publish failed, attempt " + retries + "/" + maxRetries);
                log.warn("Outbox publish failed, will retry: eventId={} attempt={}/{}",
                    fresh.getEventId(), retries, maxRetries);
            }
            outboxRepository.save(fresh);
        }
    }
}
