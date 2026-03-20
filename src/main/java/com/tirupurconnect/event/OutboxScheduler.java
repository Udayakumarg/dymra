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

/**
 * Polls outbox_events for PENDING and retryable FAILED rows.
 * Publishes each to Redis Streams, then marks PROCESSED.
 * On failure: increments retry_count, marks FAILED after maxRetryAttempts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxEventRepository outboxRepository;
    private final OutboxEventPublisher  publisher;
    private final AppProperties         props;

    @Scheduled(fixedDelayString = "${app.outbox.scheduler-delay-ms:5000}")
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> pending = outboxRepository.findPendingEvents();
        if (!pending.isEmpty()) {
            log.debug("Processing {} pending outbox events", pending.size());
            pending.forEach(this::process);
        }

        // Also retry failed events that haven't hit the limit yet
        int maxRetries = props.getOutbox().getMaxRetryAttempts();
        List<OutboxEvent> retryable = outboxRepository.findRetryableEvents(maxRetries);
        if (!retryable.isEmpty()) {
            log.debug("Retrying {} failed outbox events", retryable.size());
            retryable.forEach(this::process);
        }
    }

    private void process(OutboxEvent event) {
        boolean published = publisher.publishToStream(event);

        if (published) {
            event.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            outboxRepository.save(event);
            log.debug("Outbox event processed: eventId={} type={}", event.getEventId(), event.getEventType());
        } else {
            short retries = (short) (event.getRetryCount() + 1);
            event.setRetryCount(retries);

            int maxRetries = props.getOutbox().getMaxRetryAttempts();
            if (retries >= maxRetries) {
                event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                event.setLastError("Max retry attempts reached (" + maxRetries + ")");
                log.error("Outbox event permanently failed: eventId={} type={}",
                    event.getEventId(), event.getEventType());
            } else {
                event.setLastError("Publish failed, retry " + retries + "/" + maxRetries);
                log.warn("Outbox event publish failed, will retry: eventId={} attempt={}/{}",
                    event.getEventId(), retries, maxRetries);
            }
            outboxRepository.save(event);
        }
    }
}
