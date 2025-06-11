package org.OWA.dao;

import org.OWA.model.User;
import java.sql.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserDao {
    private static final Logger logger = LoggerFactory.getLogger(UserDao.class);
    private final Connection conn;

    public UserDao(Connection conn) {
        this.conn = conn;
    }

    public Optional<User> findByUsername(String username) throws SQLException {
        try {
            String sql = "SELECT * FROM users WHERE username = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    logger.debug("User found: {}", username);
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by username", e);
            throw e;
        }
        logger.debug("User not found: {}", username);
        return Optional.empty();
    }

    public boolean adminExists() throws SQLException {
        try {
            String sql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'";
            try (Statement st = conn.createStatement()) {
                ResultSet rs = st.executeQuery(sql);
                if (rs.next()) {
                    boolean exists = rs.getInt(1) > 0;
                    logger.debug("Admin exists: {}", exists);
                    return exists;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking admin existence", e);
            throw e;
        }
        return false;
    }

    public void save(User user) throws SQLException {
        try {
            String sql = "INSERT INTO users (username, password, role, email) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getPassword());
                ps.setString(3, user.getRole());
                ps.setString(4, user.getEmail());
                ps.executeUpdate();
                logger.info("User saved: {}", user.getUsername());
            }
        } catch (SQLException e) {
            logger.error("Error saving user", e);
            throw e;
        }
    }

    public List<User> findAllNonAdmins() throws SQLException {
        List<User> users = new ArrayList<>();
        try {
            String sql = "SELECT * FROM users WHERE role != 'ADMIN'";
            try (Statement st = conn.createStatement()) {
                ResultSet rs = st.executeQuery(sql);
                while (rs.next()) users.add(map(rs));
                logger.debug("Non-admin users found: {}", users.size());
            }
        } catch (SQLException e) {
            logger.error("Error finding non-admin users", e);
            throw e;
        }
        return users;
    }

    public void deleteById(int id) throws SQLException {
        try {
            String sql = "DELETE FROM users WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
                logger.info("User deleted by id: {}", id);
            }
        } catch (SQLException e) {
            logger.error("Error deleting user by id", e);
            throw e;
        }
    }

    private User map(ResultSet rs) throws SQLException {
        return new User(
            rs.getInt("id"),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("role"),
            rs.getString("email")
        );
    }
}
