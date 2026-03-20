package com.tirupurconnect.service;

import com.tirupurconnect.model.NotificationLog;
import com.tirupurconnect.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.whatsapp.provider", havingValue = "mock", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MockWhatsAppService implements WhatsAppService {

    private final NotificationLogRepository notificationLogRepository;

    @Override
    public void sendInquiryNotification(String phone, String businessName, String queryText, int position) {
        log.info("[WA-MOCK] Inquiry notification → {} | business={} query='{}' position={}",
            phone, businessName, queryText, position);
        saveLog(phone, "INQUIRY_NOTIFICATION", true);
    }

    @Override
    public void sendVitalityNudge(String phone, String businessName, String status) {
        log.info("[WA-MOCK] Vitality nudge → {} | business={} status={}", phone, businessName, status);
        saveLog(phone, "VITALITY_NUDGE", true);
    }

    @Override
    public void sendOtp(String phone, String otp) {
        log.info("[WA-MOCK] OTP → {} | otp={}", phone, otp);
        saveLog(phone, "OTP", true);
    }

    @Override
    public boolean checkDelivery(String phone) {
        return true;
    }

    private void saveLog(String phone, String type, boolean delivered) {
        // supplier_id is unknown at this layer; use a sentinel UUID for mock
        NotificationLog log = new NotificationLog(
            UUID.fromString("00000000-0000-0000-0000-000000000000"), type, phone
        );
        log.setDelivered(delivered);
        notificationLogRepository.save(log);
    }
}
