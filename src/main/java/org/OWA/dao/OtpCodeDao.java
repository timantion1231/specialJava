package org.OWA.dao;

import org.OWA.model.OtpCode;
import java.sql.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OtpCodeDao {
    private static final Logger logger = LoggerFactory.getLogger(OtpCodeDao.class);
    private final Connection conn;

    public OtpCodeDao(Connection conn) {
        this.conn = conn;
    }

    public void save(OtpCode code) throws SQLException {
        String sql = "INSERT INTO otp_codes (user_id, operation_id, code, status, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, code.getUserId());
            ps.setString(2, code.getOperationId());
            ps.setString(3, code.getCode());
            ps.setString(4, code.getStatus());
            ps.setTimestamp(5, Timestamp.valueOf(code.getCreatedAt()));
            ps.setTimestamp(6, Timestamp.valueOf(code.getExpiresAt()));
            ps.executeUpdate();
            logger.info("OTP code saved for user {} operation {}", code.getUserId(), code.getOperationId());
        } catch (SQLException e) {
            logger.error("Error saving OTP code", e);
            throw e;
        }
    }

    public Optional<OtpCode> findActiveByUserAndOp(int userId, String opId) throws SQLException {
        String sql = "SELECT * FROM otp_codes WHERE user_id = ? AND operation_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, opId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                logger.debug("Active OTP found for user {} operation {}", userId, opId);
                return Optional.of(map(rs));
            }
            logger.debug("No active OTP for user {} operation {}", userId, opId);
        } catch (SQLException e) {
            logger.error("Error finding active OTP", e);
            throw e;
        }
        return Optional.empty();
    }

    public void updateStatus(int id, String status) throws SQLException {
        String sql = "UPDATE otp_codes SET status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
            logger.info("OTP status updated: id={}, status={}", id, status);
        } catch (SQLException e) {
            logger.error("Error updating OTP status", e);
            throw e;
        }
    }

    public void expireOldCodes() throws SQLException {
        String sql = "UPDATE otp_codes SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND expires_at < now()";
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
            logger.info("Expired old OTP codes");
        } catch (SQLException e) {
            logger.error("Error expiring old OTP codes", e);
            throw e;
        }
    }

    public void deleteByUserId(int userId) throws SQLException {
        String sql = "DELETE FROM otp_codes WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
            logger.info("Deleted OTP codes for user {}", userId);
        } catch (SQLException e) {
            logger.error("Error deleting OTP codes by userId", e);
            throw e;
        }
    }

    private OtpCode map(ResultSet rs) throws SQLException {
        return new OtpCode(
            rs.getInt("id"),
            rs.getInt("user_id"),
            rs.getString("operation_id"),
            rs.getString("code"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("expires_at").toLocalDateTime()
        );
    }
}
