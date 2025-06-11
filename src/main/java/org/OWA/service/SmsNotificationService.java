package org.OWA.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Properties;
import java.io.InputStream;
import org.opensmpp.session.SMPPSession;
import org.opensmpp.session.Session;
import org.opensmpp.net.TCPIPConnection;
import org.opensmpp.pdu.*;

public class SmsNotificationService implements NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(SmsNotificationService.class);
    private String host;
    private int port;
    private String systemId;
    private String password;
    private String systemType;
    private String sourceAddr;
    private boolean isConfigured;

    public SmsNotificationService() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sms.properties")) {
            if (in == null) {
                logger.error("sms.properties not found");
                this.isConfigured = false;
                this.host = null;
                this.port = 0;
                this.systemId = null;
                this.password = null;
                this.systemType = null;
                this.sourceAddr = null;
                return;
            }
            props.load(in);
            this.host = props.getProperty("smpp.host");
            this.port = Integer.parseInt(props.getProperty("smpp.port", "0"));
            this.systemId = props.getProperty("smpp.system_id");
            this.password = props.getProperty("smpp.password");
            this.systemType = props.getProperty("smpp.system_type");
            this.sourceAddr = props.getProperty("smpp.source_addr");
            
            // Validate all required properties are present
            this.isConfigured = host != null && !host.isEmpty() &&
                              port > 0 &&
                              systemId != null && !systemId.isEmpty() &&
                              password != null && !password.isEmpty() &&
                              sourceAddr != null && !sourceAddr.isEmpty();
                              
            if (!this.isConfigured) {
                logger.warn("SMS service configuration is incomplete");
            }
        } catch (Exception e) {
            logger.error("Failed to load SMS configuration", e);
            this.isConfigured = false;
            this.host = null;
            this.port = 0;
            this.systemId = null;
            this.password = null;
            this.systemType = null;
            this.sourceAddr = null;
        }
    }

    @Override
    public boolean isConfigured() {
        return isConfigured;
    }

    @Override
    public void sendCode(String destination, String code) {
        if (!isConfigured()) {
            throw new IllegalStateException("SMS service is not properly configured. Check sms.properties file.");
        }

        if (destination == null || destination.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }

        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP code cannot be null or empty");
        }

        // Clean the phone number - remove any spaces, dashes, or parentheses
        destination = destination.replaceAll("[\\s\\-()]", "");

        // Validate phone number format (basic validation)
        if (!destination.matches("\\+?\\d{10,15}")) {
            throw new IllegalArgumentException("Invalid phone number format: " + destination);
        }

        try (Session session = createAndBindSession()) {
            SubmitSM submit = new SubmitSM();
            submit.setSourceAddr(sourceAddr);
            submit.setDestAddr(destination);
            submit.setShortMessage(String.format("Your verification code is: %s. Do not share this code with anyone.", code));

            Response response = session.submit(submit);
            if (response.getCommandStatus() != 0) {
                throw new RuntimeException("Failed to send SMS. SMPP error code: " + response.getCommandStatus());
            }
            
            logger.info("SMS sent successfully to {}", destination);
        } catch (Exception e) {
            logger.error("Failed to send SMS to {}: {}", destination, e.getMessage());
            throw new RuntimeException("Failed to send SMS notification: " + e.getMessage(), e);
        }
    }

    private Session createAndBindSession() throws Exception {
        TCPIPConnection connection = new TCPIPConnection(host, port);
        Session session = new Session(connection);
        
        BindRequest bindReq = new BindTransmitter();
        bindReq.setSystemId(systemId);
        bindReq.setPassword(password);
        bindReq.setSystemType(systemType);
        bindReq.setAddressRange(sourceAddr);
        
        BindResponse bindResp = session.bind(bindReq);
        if (bindResp.getCommandStatus() != 0) {
            session.close();
            throw new Exception("SMPP bind failed with status: " + bindResp.getCommandStatus());
        }
        
        return session;
    }
}
