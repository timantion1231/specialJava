package org.OWA.model;

public class OtpConfig {
    private int codeLength;
    private int ttlSeconds;

    public OtpConfig(int codeLength, int ttlSeconds) {
        this.codeLength = codeLength;
        this.ttlSeconds = ttlSeconds;
    }
    // Getters and setters...
    public int getCodeLength() { return codeLength; }
    public int getTtlSeconds() { return ttlSeconds; }
}
