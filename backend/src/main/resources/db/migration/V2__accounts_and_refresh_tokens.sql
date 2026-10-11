-- SFD-03 : comptes (RG-03-01, RG-03-03, RG-03-10, RG-00-04) et refresh tokens (RG-03-07)
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    created_by    BIGINT REFERENCES users (id),
    updated_at    TIMESTAMPTZ  NOT NULL,
    updated_by    BIGINT REFERENCES users (id),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'MANAGER', 'ADMIN'))
);

-- RG-03-01 : e-mail unique, insensible à la casse
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

CREATE TABLE refresh_tokens (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users (id),
    session_id        UUID        NOT NULL,
    token_hash        CHAR(64)    NOT NULL,
    issued_at         TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    revocation_reason VARCHAR(20),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_reason CHECK (
        revocation_reason IN ('ROTATED', 'LOGOUT', 'ACCOUNT_DEACTIVATED', 'ROLE_CHANGED', 'PASSWORD_CHANGED')),
    CONSTRAINT ck_refresh_tokens_revocation CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL))
);

CREATE INDEX ix_refresh_tokens_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
