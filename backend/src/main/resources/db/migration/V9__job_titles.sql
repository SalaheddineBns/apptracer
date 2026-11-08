-- SFD-09 : fonctions des collaborateurs (RG-09-02, RG-09-05, RG-09-06, RG-09-09, RG-09-11)
CREATE TABLE job_titles (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL,
    label      VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL,
    created_by BIGINT REFERENCES users (id),
    updated_at TIMESTAMPTZ  NOT NULL,
    updated_by BIGINT REFERENCES users (id),
    -- RG-09-05 : majuscules, chiffres et _, 2 à 30 caractères
    CONSTRAINT ck_job_titles_code CHECK (code ~ '^[A-Z0-9_]{2,30}$')
);

-- RG-09-05 : code unique sans tenir compte de la casse
CREATE UNIQUE INDEX ux_job_titles_code_lower ON job_titles (lower(code));

-- RG-09-09 : six fonctions initiales, actives ; création par le système (created_by NULL, RG-00-04)
INSERT INTO job_titles (code, label, active, created_at, updated_at)
VALUES ('DEVELOPER', 'Developer', TRUE, now(), now()),
       ('TECH_LEAD', 'Tech Lead', TRUE, now(), now()),
       ('QA', 'QA', TRUE, now(), now()),
       ('SCRUM_MASTER', 'Scrum Master', TRUE, now(), now()),
       ('PRODUCT_OWNER', 'Product Owner', TRUE, now(), now()),
       ('DATA_ANALYST', 'Data Analyst', TRUE, now(), now());

-- RG-09-02 : au plus une fonction par compte, facultative ; jamais supprimée (RG-09-06), donc pas de cascade
ALTER TABLE users ADD COLUMN job_title_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_users_job_title FOREIGN KEY (job_title_id) REFERENCES job_titles (id);
CREATE INDEX ix_users_job_title ON users (job_title_id);
-- RG-09-02 : pas de fonction pour un ADMIN
ALTER TABLE users ADD CONSTRAINT ck_users_admin_no_job_title CHECK (role <> 'ADMIN' OR job_title_id IS NULL);
