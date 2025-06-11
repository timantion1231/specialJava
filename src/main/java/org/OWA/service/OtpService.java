package org.OWA.service;

import org.OWA.dao.OtpCodeDao;
import org.OWA.dao.OtpConfigDao;
import org.OWA.model.OtpCode;
import org.OWA.model.OtpConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OtpService {
    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final OtpCodeDao otpCodeDao;
    private final OtpConfigDao otpConfigDao;

    public OtpService(Connection conn) {
        this.otpCodeDao = new OtpCodeDao(conn);
        this.otpConfigDao = new OtpConfigDao(conn);
    }

    public String generateOtp(int userId, String operationId) throws SQLException {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (operationId == null || operationId.trim().isEmpty() || operationId.length() > 64) {
            throw new IllegalArgumentException("operationId must not be empty and max 64 chars");
        }

        OtpConfig config = otpConfigDao.getConfig();
        int length = config.getCodeLength();
        if (length < 4 || length > 12) {
            throw new IllegalArgumentException("OTP length must be 4-12");
        }

        String code = randomCode(length);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusSeconds(config.getTtlSeconds());
        OtpCode otp = new OtpCode(0, userId, operationId, code, "ACTIVE", now, expires);
        otpCodeDao.save(otp);
        logger.info("Generated OTP for user {} operation {}", userId, operationId);
        return code;
    }

    public boolean validateOtp(int userId, String operationId, String code) throws SQLException {
        if (operationId == null || operationId.trim().isEmpty() || operationId.length() > 64) {
            throw new IllegalArgumentException("operationId must not be empty and max 64 chars");
        }
        if (code == null || code.trim().isEmpty() || code.length() < 4 || code.length() > 12) {
            throw new IllegalArgumentException("code must be 4-12 digits");
        }
        Optional<OtpCode> otpOpt = otpCodeDao.findActiveByUserAndOp(userId, operationId);
        if (otpOpt.isPresent() && otpOpt.get().getCode().equals(code)) {
            logger.info("OTP validated for user {} operation {}", userId, operationId);
            otpCodeDao.updateStatus(otpOpt.get().getId(), "USED");
            return true;
        }
        logger.warn("OTP validation failed for user {} operation {}", userId, operationId);
        return false;
    }

    public void expireOtps() throws SQLException {
        otpCodeDao.expireOldCodes();
        logger.info("Expired OTP codes by scheduler");
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
