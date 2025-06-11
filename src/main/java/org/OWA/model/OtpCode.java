package org.OWA.model;

import java.time.LocalDateTime;

public class OtpCode {
    private int id;
    private int userId;
    private String operationId;
    private String code;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public OtpCode(int id, int userId, String operationId, String code, String status, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.operationId = operationId;
        this.code = code;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
    // Getters and setters...
    public int getId() { return id; }
    public int getUserId() { return userId; }
    public String getOperationId() { return operationId; }
    public String getCode() { return code; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
