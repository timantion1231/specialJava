package org.OWA.service;

import org.OWA.dao.UserDao;
import org.OWA.model.User;
import org.OWA.util.JwtUtil;
import org.OWA.util.ValidationUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final int BCRYPT_WORKLOAD = 12;
    private final UserDao userDao;

    public AuthService(Connection conn) {
        this.userDao = new UserDao(conn);
    }

    public boolean register(String username, String password, String role, String email) throws SQLException {
        if (username == null || username.isBlank() || username.length() < 3 || username.length() > 64) {
            throw new IllegalArgumentException("Username must be 3-64 characters");
        }
        if (password == null || password.isBlank() || password.length() < 6 || password.length() > 128) {
            throw new IllegalArgumentException("Password must be 6-128 characters");
        }
        if (role == null || (!"ADMIN".equalsIgnoreCase(role) && !"USER".equalsIgnoreCase(role))) {
            throw new IllegalArgumentException("Role must be ADMIN or USER");
        }
        if (userDao.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        if ("ADMIN".equalsIgnoreCase(role) && userDao.adminExists()) {
            logger.warn("Attempt to register second admin: {}", username);
            return false;
        }
        if ("USER".equalsIgnoreCase(role)) {
            if (email == null || email.isEmpty() || !ValidationUtil.isValidEmail(email)) {
                logger.warn("Attempt to register USER without valid email: {}", username);
                throw new IllegalArgumentException("Email is required for USER and must be valid");
            }
        }
        String hashed = hashPassword(password);
        userDao.save(new User(0, username, hashed, role, email));
        logger.info("User registered: {}", username);
        return true;
    }

    public Optional<String> login(String username, String password) throws SQLException {
        Optional<User> userOpt = userDao.findByUsername(username);
        if (userOpt.isPresent() && checkPassword(password, userOpt.get().getPassword())) {
            logger.info("User logged in: {}", username);
            return Optional.of(JwtUtil.generateToken(userOpt.get()));
        }
        logger.warn("Login failed for user: {}", username);
        return Optional.empty();
    }

    public Optional<User> getUser(String username) throws SQLException {
        return userDao.findByUsername(username);
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_WORKLOAD));
    }

    private boolean checkPassword(String password, String hashedPassword) {
        try {
            return BCrypt.checkpw(password, hashedPassword);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid hash format encountered", e);
            return false;
        }
    }
}
