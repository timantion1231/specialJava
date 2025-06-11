package org.OWA.util;

import org.OWA.model.User;
import io.jsonwebtoken.*;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    private static final String SECRET;
    private static final long EXPIRATION;

    static {
        // Загрузка секрета из переменной окружения
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret == null || envSecret.isEmpty()) {
            logger.warn("JWT_SECRET environment variable not set, using default value (NOT SECURE FOR PRODUCTION!)");
            envSecret = "your_super_secret_jwt_key_minimum_32_chars_long";
        }
        SECRET = envSecret;

        // Загрузка времени жизни токена из переменной окружения или использование значения по умолчанию
        String envExpiration = System.getenv("JWT_EXPIRATION_MS");
        long expiration = 1800000; // 30 минут по умолчанию
        if (envExpiration != null) {
            try {
                expiration = Long.parseLong(envExpiration);
            } catch (NumberFormatException e) {
                logger.warn("Invalid JWT_EXPIRATION_MS value, using default: 30 minutes");
            }
        }
        EXPIRATION = expiration;
    }

    public static String generateToken(User user) {
        return Jwts.builder()
            .setSubject(user.getUsername())
            .claim("role", user.getRole())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
            .signWith(SignatureAlgorithm.HS256, SECRET)
            .compact();
    }

    public static Jws<Claims> parseToken(String token) {
        try {
            return Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token);
        } catch (ExpiredJwtException e) {
            logger.warn("Expired JWT token");
            throw e;
        } catch (UnsupportedJwtException e) {
            logger.warn("Unsupported JWT token");
            throw e;
        } catch (MalformedJwtException e) {
            logger.warn("Malformed JWT token");
            throw e;
        } catch (SignatureException e) {
            logger.warn("Invalid JWT signature");
            throw e;
        } catch (IllegalArgumentException e) {
            logger.warn("JWT claims string is empty");
            throw e;
        }
    }

    public static boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token).getBody();
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            logger.error("Error checking token expiration", e);
            return true;
        }
    }
}
