package com.tirupurconnect.service;

public interface WhatsAppService {
    void sendInquiryNotification(String phone, String businessName, String queryText, int position);
    void sendVitalityNudge(String phone, String businessName, String status);
    void sendOtp(String phone, String otp);
    boolean checkDelivery(String phone);
}
