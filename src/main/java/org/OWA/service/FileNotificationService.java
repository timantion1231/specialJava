package org.OWA.service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileNotificationService implements NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(FileNotificationService.class);
    private final String directory;
    private final boolean isConfigured;

    public FileNotificationService() {
        Properties props = new Properties();
        String configuredDir;
        
        try (var in = FileNotificationService.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
                configuredDir = props.getProperty("otp.file.storage.path");
                if (configuredDir == null || configuredDir.trim().isEmpty()) {
                    logger.warn("otp.file.storage.path not configured, using default directory");
                    configuredDir = "./otp_codes";
                }
            } else {
                logger.warn("application.properties not found, using default directory");
                configuredDir = "./otp_codes";
            }
        } catch (Exception e) {
            logger.warn("Could not load application.properties, using default directory", e);
            configuredDir = "./otp_codes";
        }

        this.directory = configuredDir;
        
        // Проверяем доступность директории
        try {
            Path dirPath = new File(directory).toPath();
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            if (!Files.isWritable(dirPath)) {
                logger.error("Directory {} is not writable", directory);
                this.isConfigured = false;
                return;
            }
            this.isConfigured = true;
            logger.info("File notification service configured with directory: {}", directory);
        } catch (Exception e) {
            logger.error("Failed to setup file notification directory: {}", directory, e);
            this.isConfigured = false;
        }
    }

    @Override
    public void sendCode(String destination, String code) {
        if (!isConfigured()) {
            throw new IllegalStateException("File notification service is not properly configured");
        }

        File dir = new File(directory);
        String sanitizedUsername = sanitizeFilename(destination);
        String timestamp = String.valueOf(System.currentTimeMillis());
        File file = new File(dir, String.format("otp_%s_%s.txt", sanitizedUsername, timestamp));

        try (FileWriter fw = new FileWriter(file)) {
            fw.write(String.format("OTP code for %s%nCode: %s%nGenerated: %s%n", 
                destination, code, new java.util.Date()));
            logger.info("OTP code saved to file for user {} at {}", destination, file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save OTP to file for user {}", destination, e);
            throw new RuntimeException("Failed to save OTP to file", e);
        }
    }

    @Override
    public boolean isConfigured() {
        return isConfigured;
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
