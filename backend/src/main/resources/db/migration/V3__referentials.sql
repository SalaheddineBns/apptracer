-- SFD-02 : référentiels (RG-02-01 à RG-02-05, RG-00-04)
CREATE TABLE activity_types (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code             VARCHAR(30)  NOT NULL,
    label            VARCHAR(100) NOT NULL,
    project_required BOOLEAN      NOT NULL,
    color            VARCHAR(10)  NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    created_by       BIGINT REFERENCES users (id),
    updated_at       TIMESTAMPTZ  NOT NULL,
    updated_by       BIGINT REFERENCES users (id),
    -- RG-02-01 : code unique dans son référentiel, majuscules, chiffres et _, 2 à 30 caractères
    CONSTRAINT ux_activity_types_code UNIQUE (code),
    CONSTRAINT ck_activity_types_code CHECK (code ~ '^[A-Z0-9_]{2,30}$'),
    -- RG-02-04 : palette du design system
    CONSTRAINT ck_activity_types_color CHECK (color IN ('INFO', 'WARN', 'SECONDARY', 'CONTRAST', 'SUCCESS'))
);

CREATE TABLE projects (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL,
    created_by BIGINT REFERENCES users (id),
    updated_at TIMESTAMPTZ  NOT NULL,
    updated_by BIGINT REFERENCES users (id),
    -- RG-02-01
    CONSTRAINT ux_projects_code UNIQUE (code),
    CONSTRAINT ck_projects_code CHECK (code ~ '^[A-Z0-9_]{2,30}$')
);

-- RG-02-02, RG-02-03, RG-02-04 : jeu initial, tous actifs ; création par le système (created_by NULL, RG-00-04)
INSERT INTO activity_types (code, label, project_required, color, active, created_at, updated_at)
VALUES ('TRAVAIL_PROJET', 'Travail projet', TRUE, 'INFO', TRUE, now(), now()),
       ('REUNION', 'Réunion', FALSE, 'WARN', TRUE, now(), now()),
       ('LECTURE_SFD', 'Lecture de SFD', FALSE, 'SECONDARY', TRUE, now(), now()),
       ('REVUE_MR', 'Revue de MR', TRUE, 'CONTRAST', TRUE, now(), now()),
       ('FORMATION', 'Formation', FALSE, 'SUCCESS', TRUE, now(), now()),
       ('AUTRE', 'Autre', FALSE, 'SECONDARY', TRUE, now(), now());
