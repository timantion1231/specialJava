package org.OWA.dao;

import org.OWA.model.OtpConfig;
import java.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OtpConfigDao {
    private static final Logger logger = LoggerFactory.getLogger(OtpConfigDao.class);
    private final Connection conn;

    public OtpConfigDao(Connection conn) {
        this.conn = conn;
    }

    public OtpConfig getConfig() throws SQLException {
        String sql = "SELECT * FROM otp_config WHERE id = 1";
        try (Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery(sql);
            if (rs.next()) {
                OtpConfig config = new OtpConfig(rs.getInt("code_length"), rs.getInt("ttl_seconds"));
                logger.debug("OTP config loaded: codeLength={}, ttlSeconds={}", config.getCodeLength(), config.getTtlSeconds());
                return config;
            }
        }
        // Default config
        insertDefaultConfig();
        logger.info("Default OTP config created: codeLength=6, ttlSeconds=300");
        return new OtpConfig(6, 300);
    }

    private void insertDefaultConfig() throws SQLException {
        String insert = "INSERT INTO otp_config (id, code_length, ttl_seconds) VALUES (1, 6, 300) ON CONFLICT (id) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.executeUpdate();
        }
    }

    public void updateConfig(int codeLength, int ttlSeconds) throws SQLException {
        String sql = "UPDATE otp_config SET code_length = ?, ttl_seconds = ? WHERE id = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, codeLength);
            ps.setInt(2, ttlSeconds);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                String insert = "INSERT INTO otp_config (id, code_length, ttl_seconds) VALUES (1, ?, ?) ON CONFLICT (id) DO UPDATE SET code_length = EXCLUDED.code_length, ttl_seconds = EXCLUDED.ttl_seconds";
                try (PreparedStatement ins = conn.prepareStatement(insert)) {
                    ins.setInt(1, codeLength);
                    ins.setInt(2, ttlSeconds);
                    ins.executeUpdate();
                }
            }
        }
        logger.info("OTP config updated: codeLength={}, ttlSeconds={}", codeLength, ttlSeconds);
    }
}
