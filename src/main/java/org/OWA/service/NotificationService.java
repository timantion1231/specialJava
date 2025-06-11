package org.OWA.service;

public interface NotificationService {
    void sendCode(String destination, String code);
    boolean isConfigured();
}
