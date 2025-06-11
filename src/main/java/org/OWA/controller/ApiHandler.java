package org.OWA.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.OWA.service.AuthService;
import org.OWA.service.OtpService;
import org.OWA.service.FileNotificationService;
import org.OWA.service.EmailNotificationService;
import org.OWA.service.SmsNotificationService;
import org.OWA.service.TelegramNotificationService;
import org.OWA.util.JwtUtil;
import org.OWA.model.User;
import org.OWA.dao.UserDao;
import org.OWA.dao.OtpConfigDao;
import org.OWA.dao.OtpCodeDao;
import io.jsonwebtoken.Claims;
import java.io.*;
import java.net.URI;
import java.sql.Connection;
import java.util.*;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.OWA.util.ValidationUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ApiHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiHandler.class);
    private final AuthService authService;
    private final OtpService otpService;
    private final OtpConfigDao otpConfigDao;
    private final UserDao userDao;
    private final OtpCodeDao otpCodeDao;
    private final FileNotificationService fileNotificationService;
    private final EmailNotificationService emailNotificationService;
    private final SmsNotificationService smsNotificationService;
    private final TelegramNotificationService telegramNotificationService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public ApiHandler(Connection conn) {
        this.authService = new AuthService(conn);
        this.otpService = new OtpService(conn);
        this.otpConfigDao = new OtpConfigDao(conn);
        this.userDao = new UserDao(conn);
        this.otpCodeDao = new OtpCodeDao(conn);
        this.fileNotificationService = new FileNotificationService();
        this.emailNotificationService = new EmailNotificationService();
        this.smsNotificationService = new SmsNotificationService();
        this.telegramNotificationService = new TelegramNotificationService();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        String method = exchange.getRequestMethod();
        logger.info("Incoming request: {} {}", method, path);

        if (path.equals("/api/register") && method.equals("POST")) {
            handleRegister(exchange);
        } else if (path.equals("/api/login") && method.equals("POST")) {
            handleLogin(exchange);
        } else if (path.startsWith("/api/admin")) {
            handleAdmin(exchange, path, method);
        } else if (path.startsWith("/api/user")) {
            handleUser(exchange, path, method);
        } else {
            sendResponse(exchange, 404, "Not found");
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseBody(exchange);
        String username = params.get("username");
        String password = params.get("password");
        String role = params.get("role");
        String email = params.get("email");
        if (username == null || username.isBlank() ||
            password == null || password.isBlank() ||
            role == null || role.isBlank() ||
            (!"ADMIN".equalsIgnoreCase(role) && (email == null || email.isBlank()))) {
            sendResponse(exchange, 400, "Missing required fields");
            return;
        }
        if (!"ADMIN".equalsIgnoreCase(role) && !"USER".equalsIgnoreCase(role)) {
            sendResponse(exchange, 400, "Role must be ADMIN or USER");
            return;
        }
        try {
            if (userDao.findByUsername(username).isPresent()) {
                sendResponse(exchange, 400, "Username already exists");
                return;
            }
        } catch (Exception e) {
            logger.error("Error checking username uniqueness", e);
            sendResponse(exchange, 500, "Error: " + e.getMessage());
            return;
        }
        if ("ADMIN".equalsIgnoreCase(role)) {
            try {
                if (userDao.adminExists()) {
                    sendResponse(exchange, 400, "Admin already exists");
                    return;
                }
            } catch (Exception e) {
                logger.error("Error checking admin existence", e);
                sendResponse(exchange, 500, "Error: " + e.getMessage());
                return;
            }
        }
        if ("USER".equalsIgnoreCase(role) && !ValidationUtil.isValidEmail(email)) {
            sendResponse(exchange, 400, "Invalid email format");
            return;
        }
        try {
            boolean ok = authService.register(username, password, role, email);
            if (ok) {
                logger.info("User registered: {}", username);
                sendResponse(exchange, 200, "Registered");
            } else {
                logger.warn("Attempt to register second admin: {}", username);
                sendResponse(exchange, 400, "Admin already exists");
            }
        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 400, e.getMessage());
        } catch (Exception e) {
            logger.error("Registration error", e);
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseBody(exchange);
        String username = params.get("username");
        String password = params.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            sendResponse(exchange, 400, "Missing username or password");
            return;
        }
        try {
            Optional<String> token = authService.login(username, password);
            if (token.isPresent()) {
                logger.info("User logged in: {}", username);
                sendResponse(exchange, 200, token.get());
            } else {
                logger.warn("Invalid login attempt: {}", username);
                sendResponse(exchange, 401, "Invalid credentials");
            }
        } catch (Exception e) {
            logger.error("Login error", e);
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    private void handleAdmin(HttpExchange exchange, String path, String method) throws IOException {
        Optional<User> user = authenticate(exchange, "ADMIN");
        if (user.isEmpty()) return;
        try {
            if (path.equals("/api/admin/config") && method.equals("POST")) {
                Map<String, String> params = parseBody(exchange);
                if (!params.containsKey("codeLength") || !params.containsKey("ttlSeconds")) {
                    sendResponse(exchange, 400, "Missing config parameters");
                    return;
                }
                int codeLength, ttlSeconds;
                try {
                    codeLength = Integer.parseInt(params.get("codeLength"));
                    ttlSeconds = Integer.parseInt(params.get("ttlSeconds"));
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "Config parameters must be integers");
                    return;
                }
                if (codeLength < 4 || codeLength > 12 || ttlSeconds < 30 || ttlSeconds > 3600) {
                    sendResponse(exchange, 400, "Invalid config values");
                    return;
                }
                otpConfigDao.updateConfig(codeLength, ttlSeconds);
                logger.info("OTP config updated by admin");
                sendResponse(exchange, 200, "Config updated");            } else if (path.equals("/api/admin/users") && method.equals("GET")) {
                List<User> users = userDao.findAllNonAdmins();
                String json = objectMapper.writeValueAsString(Map.of("users", users));
                logger.info("Admin requested user list");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(exchange, 200, json);
            } else if (path.equals("/api/admin/deleteUser") && method.equals("POST")) {
                Map<String, String> params = parseBody(exchange);
                String username = params.get("username");
                if (username == null || username.isBlank()) {
                    sendResponse(exchange, 400, "Missing username");
                    return;
                }
                Optional<User> delUser = userDao.findByUsername(username);
                if (delUser.isPresent()) {
                    if ("ADMIN".equalsIgnoreCase(delUser.get().getRole())) {
                        sendResponse(exchange, 400, "Cannot delete admin");
                        return;
                    }
                    // Запретить удалять самого себя
                    if (user.get().getUsername().equalsIgnoreCase(username)) {
                        sendResponse(exchange, 400, "Cannot delete yourself");
                        return;
                    }
                    otpCodeDao.deleteByUserId(delUser.get().getId());
                    userDao.deleteById(delUser.get().getId());
                    logger.info("User deleted by admin: {}", username);
                    sendResponse(exchange, 200, "User deleted");
                } else {
                    logger.warn("Attempt to delete non-existent user: {}", username);
                    sendResponse(exchange, 400, "User not found or is admin");
                }
            } else {
                sendResponse(exchange, 404, "Admin endpoint not found");
            }
        } catch (Exception e) {
            logger.error("Admin API error", e);
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    private void handleUser(HttpExchange exchange, String path, String method) throws IOException {
        Optional<User> user = authenticate(exchange, "USER_OR_ADMIN");
        if (user.isEmpty()) return;
        try {
            if (path.equals("/api/user/generateOtp") && method.equals("POST")) {
                Map<String, String> params = parseBody(exchange);
                String operationId = params.get("operationId");
                String channel = params.get("channel");
                if (operationId == null || operationId.isBlank() || channel == null || channel.isBlank()) {
                    sendResponse(exchange, 400, "Missing operationId or channel");
                    return;
                }
                if (operationId.length() < 1 || operationId.length() > 64) {
                    sendResponse(exchange, 400, "Invalid operationId length");
                    return;
                }
                if (!List.of("file", "email", "sms", "telegram").contains(channel.toLowerCase())) {
                    sendResponse(exchange, 400, "Unknown channel");
                    return;
                }
                String code = otpService.generateOtp(user.get().getId(), operationId);
                if ("file".equalsIgnoreCase(channel)) {
                    if (!fileNotificationService.isConfigured()) {
                        sendResponse(exchange, 500, "File service not configured");
                        return;
                    }
                    fileNotificationService.sendCode(user.get().getUsername(), code);
                    logger.info("OTP saved to file for user {}", user.get().getUsername());
                    sendResponse(exchange, 200, "OTP saved to file");
                } else if ("email".equalsIgnoreCase(channel)) {
                    if (!emailNotificationService.isConfigured()) {
                        sendResponse(exchange, 500, "Email service not configured");
                        return;
                    }
                    if (user.get().getEmail() == null || user.get().getEmail().isBlank()) {
                        logger.warn("No email for user {}", user.get().getUsername());
                        sendResponse(exchange, 400, "No email for user");
                        return;
                    }
                    emailNotificationService.sendCode(user.get().getEmail(), code);
                    logger.info("OTP sent via email to {}", user.get().getEmail());
                    sendResponse(exchange, 200, "OTP sent via email");
                } else if ("sms".equalsIgnoreCase(channel)) {
                    if (!smsNotificationService.isConfigured()) {
                        sendResponse(exchange, 500, "SMS service not configured");
                        return;
                    }
                    smsNotificationService.sendCode(user.get().getUsername(), code);
                    logger.info("OTP sent via SMS to {}", user.get().getUsername());
                    sendResponse(exchange, 200, "OTP sent via SMS");
                } else if ("telegram".equalsIgnoreCase(channel)) {
                    if (!telegramNotificationService.isConfigured()) {
                        sendResponse(exchange, 500, "Telegram service not configured");
                        return;
                    }
                    telegramNotificationService.sendCode(user.get().getUsername(), code);
                    logger.info("OTP sent via Telegram to {}", user.get().getUsername());
                    sendResponse(exchange, 200, "OTP sent via Telegram");
                } else {
                    logger.warn("Unknown channel: {}", channel);
                    sendResponse(exchange, 400, "Unknown channel");
                }
            } else if (path.equals("/api/user/validateOtp") && method.equals("POST")) {
                Map<String, String> params = parseBody(exchange);
                String operationId = params.get("operationId");
                String code = params.get("code");
                if (operationId == null || operationId.isBlank() || code == null || code.isBlank()) {
                    sendResponse(exchange, 400, "Missing operationId or code");
                    return;
                }
                if (operationId.length() < 1 || operationId.length() > 64) {
                    sendResponse(exchange, 400, "Invalid operationId length");
                    return;
                }
                if (code.length() < 4 || code.length() > 12) {
                    sendResponse(exchange, 400, "Invalid code length");
                    return;
                }
                boolean valid = otpService.validateOtp(user.get().getId(), operationId, code);
                if (valid) {
                    logger.info("OTP validated for user {}", user.get().getUsername());
                    sendResponse(exchange, 200, "OTP valid");
                } else {
                    logger.warn("OTP invalid or expired for user {}", user.get().getUsername());
                    sendResponse(exchange, 400, "OTP invalid or expired");
                }
            } else {
                sendResponse(exchange, 404, "User endpoint not found");
            }
        } catch (Exception e) {
            logger.error("User API error", e);
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }

    private Optional<User> authenticate(HttpExchange exchange, String requiredRole) throws IOException {
        List<String> authHeaders = exchange.getRequestHeaders().get("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            sendResponse(exchange, 401, "Missing token");
            return Optional.empty();
        }
        String token = authHeaders.get(0).replace("Bearer ", "").trim();
        if (token.isEmpty()) {
            sendResponse(exchange, 401, "Missing token");
            return Optional.empty();
        }
        try {
            if (JwtUtil.isTokenExpired(token)) {
                sendResponse(exchange, 401, "Token expired");
                return Optional.empty();
            }
            Claims claims = JwtUtil.parseToken(token).getBody();
            String username = claims.getSubject();
            String role = (String) claims.get("role");
            if ("ADMIN".equalsIgnoreCase(requiredRole) && !"ADMIN".equalsIgnoreCase(role)) {
                sendResponse(exchange, 403, "Forbidden");
                return Optional.empty();
            }
            if ("USER".equalsIgnoreCase(requiredRole) && !"USER".equalsIgnoreCase(role)) {
                sendResponse(exchange, 403, "Forbidden");
                return Optional.empty();
            }
            if ("USER_OR_ADMIN".equals(requiredRole) &&
                !("USER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role))) {
                sendResponse(exchange, 403, "Forbidden");
                return Optional.empty();
            }
            Optional<User> user = userDao.findByUsername(username);
            if (user.isEmpty()) {
                sendResponse(exchange, 401, "User not found");
                return Optional.empty();
            }
            return user;
        } catch (Exception e) {
            logger.warn("Token authentication failed: {}", e.getMessage());
            sendResponse(exchange, 401, "Invalid token");
            return Optional.empty();
        }
    }    private Map<String, String> parseBody(HttpExchange exchange) throws IOException {
        Map<String, String> map = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            
            String bodyStr = body.toString();
            if (!bodyStr.isEmpty()) {
                for (String pair : bodyStr.split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                        String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        map.put(key, value);
                    }
                }
            }
        }
        
        return map;
    }

    private void sendResponse(HttpExchange exchange, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        logger.debug("Response {}: {}", code, msg);
    }
}
