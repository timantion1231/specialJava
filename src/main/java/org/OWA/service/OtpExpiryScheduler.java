package org.OWA.service;

import java.sql.Connection;
import java.util.Timer;
import java.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OtpExpiryScheduler {
    private static final Logger logger = LoggerFactory.getLogger(OtpExpiryScheduler.class);
    private final Timer timer = new Timer(true);

    public OtpExpiryScheduler(Connection conn, OtpService otpService) {
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                try {
                    otpService.expireOtps();
                } catch (Exception e) {
                    logger.error("Error expiring OTP codes", e);
                }
            }
        }, 0, 60_000); // every 60 seconds
    }

    public void shutdown() {
        timer.cancel();
        logger.info("OtpExpiryScheduler stopped");
    }
}
