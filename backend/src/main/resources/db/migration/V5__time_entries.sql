-- SFD-01 : saisies d'activité (RG-01-01 à RG-01-03, RG-01-08, RG-01-09, RG-01-11, RG-00-04)
-- La colonne générée `occupied` et la contrainte de non-chevauchement (RG-01-06) viennent avec V6.
CREATE TABLE time_entries (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              BIGINT       NOT NULL REFERENCES users (id),
    granularity          VARCHAR(5)   NOT NULL,
    start_date           DATE         NOT NULL,
    -- égale à start_date hors RANGE, pour uniformiser les requêtes de période
    end_date             DATE         NOT NULL,
    start_time           TIME,
    end_time             TIME,
    activity_type_id     BIGINT       NOT NULL REFERENCES activity_types (id),
    project_id           BIGINT REFERENCES projects (id),
    -- RG-01-09
    comment              VARCHAR(500),
    -- RG-01-08 : durée et journée standard figées à l'enregistrement
    duration_minutes     INTEGER      NOT NULL,
    standard_day_minutes INTEGER      NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    -- RG-01-11, RG-00-04
    created_at           TIMESTAMPTZ  NOT NULL,
    created_by           BIGINT       NOT NULL REFERENCES users (id),
    updated_at           TIMESTAMPTZ  NOT NULL,
    updated_by           BIGINT       NOT NULL REFERENCES users (id),
    deleted_at           TIMESTAMPTZ,
    deleted_by           BIGINT REFERENCES users (id),
    CONSTRAINT ck_time_entries_granularity CHECK (granularity IN ('HOUR', 'DAY', 'RANGE')),
    -- RG-01-01, RG-01-02 : heures renseignées (fin après début) en HOUR seulement
    CONSTRAINT ck_time_entries_hour_times CHECK (
        (granularity = 'HOUR' AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
        OR (granularity <> 'HOUR' AND start_time IS NULL AND end_time IS NULL)),
    -- RG-01-01 : pas de 15 minutes, secondes à 0 (900 s = 15 min)
    CONSTRAINT ck_time_entries_quarter_step CHECK (
        granularity <> 'HOUR'
        OR (EXTRACT(EPOCH FROM start_time) % 900 = 0 AND EXTRACT(EPOCH FROM end_time) % 900 = 0)),
    -- RG-01-02, RG-01-03 : RANGE = 2 à 31 jours calendaires bornes incluses ; sinon un seul jour
    CONSTRAINT ck_time_entries_dates CHECK (
        (granularity = 'RANGE' AND end_date > start_date AND end_date - start_date <= 30)
        OR (granularity <> 'RANGE' AND end_date = start_date)),
    CONSTRAINT ck_time_entries_duration CHECK (duration_minutes >= 0),
    CONSTRAINT ck_time_entries_standard_day CHECK (standard_day_minutes > 0),
    -- RG-01-11 : suppression logique, qui et quand ensemble
    CONSTRAINT ck_time_entries_deletion CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

-- RG-02-07 : un type est-il utilisé ? ; intégrité référentielle
CREATE INDEX ix_time_entries_activity_type ON time_entries (activity_type_id);
CREATE INDEX ix_time_entries_project ON time_entries (project_id);
