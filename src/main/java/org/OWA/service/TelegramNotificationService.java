package org.OWA.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Properties;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

public class TelegramNotificationService implements NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(TelegramNotificationService.class);
    private final String apiUrl;
    private final String chatId;
    private final boolean isConfigured;

    public TelegramNotificationService() {
        String tempApiUrl = null;
        String tempChatId = null;
        boolean isValid = false;

        try {
            Properties props = new Properties();
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("telegram.properties")) {
                if (in != null) {
                    props.load(in);
                    tempApiUrl = props.getProperty("telegram.api.url");
                    tempChatId = props.getProperty("telegram.chat.id");

                    if (tempApiUrl != null && !tempApiUrl.trim().isEmpty() &&
                            tempChatId != null && !tempChatId.trim().isEmpty()) {
                        // Проверяем валидность URL
                        if (tempApiUrl.startsWith("https://api.telegram.org/bot")) {
                            isValid = true;
                        } else {
                            logger.error("Invalid Telegram API URL format");
                        }
                    } else {
                        logger.error("Missing Telegram configuration parameters");
                    }
                } else {
                    logger.error("telegram.properties not found");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load Telegram configuration", e);
        }

        this.apiUrl = tempApiUrl;
        this.chatId = tempChatId;
        this.isConfigured = isValid;

        if (isConfigured) {
            logger.info("Telegram notification service configured successfully");
        } else {
            logger.warn("Telegram notification service is not properly configured");
        }
    }

    @Override
    public void sendCode(String destination, String code) {
        if (!isConfigured()) {
            throw new IllegalStateException("Telegram service is not properly configured");
        }

        String message = String.format("%s, your confirmation code is: %s", destination, code);
        String url = String.format("%s?chat_id=%s&text=%s",
                apiUrl, urlEncode(chatId), urlEncode(message));

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());

                if (statusCode != 200) {
                    logger.error("Telegram API error. Status code: {}, Response: {}", statusCode, responseBody);
                    throw new RuntimeException("Failed to send Telegram message: " + responseBody);
                }

                logger.info("Telegram message sent successfully to {}", destination);
            }
        } catch (Exception e) {
            logger.error("Error sending Telegram message to {}: {}", destination, e.getMessage());
            throw new RuntimeException("Failed to send Telegram message", e);
        }
    }

    @Override
    public boolean isConfigured() {
        return isConfigured;
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            logger.error("Error encoding URL parameter: {}", e.getMessage());
            throw new RuntimeException("URL encoding failed", e);
        }
    }
}
