package org.OWA.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static HikariDataSource dataSource;

    static {
        try {
            Properties props = new Properties();
            try (var in = DatabaseConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
                if (in != null) {
                    props.load(in);
                } else {
                    logger.error("application.properties not found");
                    throw new RuntimeException("application.properties not found");
                }
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(getRequiredProperty(props, "db.url"));
            config.setUsername(getRequiredProperty(props, "db.username"));
            config.setPassword(getRequiredProperty(props, "db.password"));
            
            // Настройки пула соединений
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(5);
            config.setIdleTimeout(300000); // 5 минут
            config.setMaxLifetime(600000); // 10 минут
            config.setConnectionTimeout(30000); // 30 секунд
            
            dataSource = new HikariDataSource(config);
            logger.info("Database connection pool initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize database connection pool", e);
            throw new RuntimeException("Failed to initialize database connection pool", e);
        }
    }

    private static String getRequiredProperty(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("Required property '" + key + "' not found");
        }
        return value;
    }

    public static Connection getConnection() throws SQLException {
        try {
            Connection conn = dataSource.getConnection();
            conn.setAutoCommit(false); // Включаем транзакции
            return conn;
        } catch (SQLException e) {
            logger.error("Failed to get database connection", e);
            throw e;
        }
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }
}
