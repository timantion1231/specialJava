package org.OWA;

import com.sun.net.httpserver.HttpServer;
import org.OWA.controller.ApiHandler;
import org.OWA.service.OtpService;
import org.OWA.service.OtpExpiryScheduler;
import org.OWA.util.DatabaseConfig;
import java.net.InetSocketAddress;
import java.sql.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.Executors;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    private static final int THREAD_POOL_SIZE = Integer.parseInt(System.getenv().getOrDefault("THREAD_POOL_SIZE", "10"));
    private static final int SHUTDOWN_TIMEOUT = Integer.parseInt(System.getenv().getOrDefault("SHUTDOWN_TIMEOUT_SECONDS", "3"));
    
    private static HttpServer server;
    private static OtpExpiryScheduler scheduler;

    public static void main(String[] args) {
        Connection apiConn = null;
        Connection schedulerConn = null;
        try {
            // Инициализируем пул соединений и проверяем версию PostgreSQL
            try (Connection conn = DatabaseConfig.getConnection()) {
                String dbVersion = conn.getMetaData().getDatabaseProductVersion();
                if (!dbVersion.startsWith("17.")) {
                    throw new RuntimeException("PostgreSQL version must be 17, but found: " + dbVersion);
                }
                logger.info("Connected to PostgreSQL version {}", dbVersion);
            }

            // Создаем подключение для API handler'а через пул соединений
            apiConn = DatabaseConfig.getConnection();
            
            // Создаем и настраиваем HTTP сервер
            server = HttpServer.create(new InetSocketAddress(SERVER_PORT), 0);
            server.createContext("/api", new ApiHandler(apiConn));
            server.setExecutor(Executors.newFixedThreadPool(THREAD_POOL_SIZE));
            server.start();

            // Запускаем планировщик для очистки устаревших OTP
            schedulerConn = DatabaseConfig.getConnection();
            scheduler = new OtpExpiryScheduler(
                schedulerConn,
                new OtpService(schedulerConn)
            );

            // Добавляем обработчик завершения
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    shutdown();
                    if (apiConn != null && !apiConn.isClosed()) apiConn.close();
                    if (schedulerConn != null && !schedulerConn.isClosed()) schedulerConn.close();
                } catch (Exception e) {
                    logger.error("Error during shutdown", e);
                }
            }));

            logger.info("Server started on port {}", SERVER_PORT);
        } catch (Exception e) {
            logger.error("Failed to start server", e);
            try {
                if (apiConn != null && !apiConn.isClosed()) apiConn.close();
                if (schedulerConn != null && !schedulerConn.isClosed()) schedulerConn.close();
            } catch (Exception ex) {
                logger.error("Error closing DB connections", ex);
            }
            System.exit(1);
        }
    }

    private static void shutdown() {
        logger.info("Shutting down server...");
        try {
            if (server != null) {
                server.stop(SHUTDOWN_TIMEOUT);
            }
            if (scheduler != null) {
                scheduler.shutdown();
            }
            DatabaseConfig.closePool();
            logger.info("Server shutdown complete");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }
}
