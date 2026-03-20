package com.tirupurconnect.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

// FIX #16: Removed NotificationLog persistence from mock impl.
// Sentinel UUID "00000000-..." violates FK constraint on supplier_id.
// Mock service only logs — NotificationLog is written by real impl or integration tests.
@Service
@ConditionalOnProperty(name = "app.whatsapp.provider", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockWhatsAppService implements WhatsAppService {

    @Override
    public void sendInquiryNotification(String phone, String businessName,
                                         String queryText, int position) {
        log.info("[WA-MOCK] Inquiry notification → phone={} business='{}' query='{}' position={}",
            phone, businessName, queryText, position);
    }

    @Override
    public void sendVitalityNudge(String phone, String businessName, String status) {
        log.info("[WA-MOCK] Vitality nudge → phone={} business='{}' status={}",
            phone, businessName, status);
    }

    @Override
    public void sendOtp(String phone, String otp) {
        // In dev: OTP is visible in logs. Never log OTPs in production.
        log.info("[WA-MOCK] OTP → phone={} otp={}", phone, otp);
    }

    @Override
    public boolean checkDelivery(String phone) {
        return true;
    }
}
