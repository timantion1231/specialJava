package org.OWA.service;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailNotificationService implements NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);
    private final String username;
    private final String password;
    private final String fromEmail;
    private final Session session;
    private boolean isInitialized;

    public EmailNotificationService() {
        Properties config = loadConfig();
        this.username = config.getProperty("email.username");
        this.password = config.getProperty("email.password");
        this.fromEmail = config.getProperty("email.from");
        
        if (isConfigured()) {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", config.getProperty("email.smtp.host", "smtp.gmail.com"));
            props.put("mail.smtp.port", config.getProperty("email.smtp.port", "587"));
            
            this.session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
            this.isInitialized = true;
        } else {
            this.session = null;
            this.isInitialized = false;
            logger.warn("Email notification service not properly configured");
        }
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        try {
            try (var in = EmailNotificationService.class.getClassLoader()
                    .getResourceAsStream("email.properties")) {
                if (in == null) {
                    logger.warn("email.properties not found in classpath");
                    return props;
                }
                props.load(in);
            }
        } catch (Exception e) {
            logger.error("Failed to load email configuration", e);
        }
        return props;
    }

    @Override
    public void sendCode(String toEmail, String code) {
        if (!isInitialized) {
            throw new IllegalStateException("Email notification service is not properly configured");
        }
        
        if (toEmail == null || toEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address cannot be null or empty");
        }
        
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP code cannot be null or empty");
        }

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("Your OTP Code");
            
            // Create HTML content for better presentation
            String htmlContent = String.format(
                "<html><body>" +
                "<h2>Your OTP Verification Code</h2>" +
                "<p>Your verification code is: <strong>%s</strong></p>" +
                "<p>This code will expire soon. Please do not share this code with anyone.</p>" +
                "</body></html>",
                code
            );

            message.setContent(htmlContent, "text/html; charset=utf-8");

            Transport.send(message);
            logger.info("OTP email sent successfully to {}", toEmail);
        } catch (AddressException e) {
            logger.error("Invalid email address: {}", toEmail, e);
            throw new IllegalArgumentException("Invalid email address: " + toEmail, e);
        } catch (MessagingException e) {
            logger.error("Failed to send email to {}", toEmail, e);
            throw new RuntimeException("Failed to send email notification", e);
        }
    }

    @Override
    public boolean isConfigured() {
        return username != null && !username.isEmpty() &&
               password != null && !password.isEmpty() &&
               fromEmail != null && !fromEmail.isEmpty();
    }
}
