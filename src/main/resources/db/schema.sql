CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    email VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS otp_config (
    id SERIAL PRIMARY KEY,
    code_length INT NOT NULL,
    ttl_seconds INT NOT NULL,
    CONSTRAINT otp_config_singleton CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS otp_codes (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    operation_id VARCHAR(255),
    code VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS one_admin ON users ((role)) WHERE role = 'ADMIN';
CREATE UNIQUE INDEX IF NOT EXISTS otp_config_single ON otp_config((id)) WHERE id = 1;

CREATE INDEX IF NOT EXISTS idx_otp_codes_user_id ON otp_codes(user_id);
CREATE INDEX IF NOT EXISTS idx_otp_codes_user_op ON otp_codes(user_id, operation_id);
CREATE INDEX IF NOT EXISTS idx_otp_codes_status_expires ON otp_codes(status, expires_at) WHERE status = 'ACTIVE';
