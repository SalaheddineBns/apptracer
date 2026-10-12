-- SFD-06 : paramètres de l'application, table à une seule ligne
CREATE TABLE app_settings (
    id                   SMALLINT    NOT NULL PRIMARY KEY,
    standard_day_minutes INTEGER     NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0,
    -- RG-06-05 : dernière modification (qui, quand), vide tant que la valeur n'a jamais été modifiée
    updated_at           TIMESTAMPTZ,
    updated_by           BIGINT REFERENCES users (id),
    -- une seule ligne possible
    CONSTRAINT ck_app_settings_single_row CHECK (id = 1),
    -- RG-06-02, RG-00-02 : de 60 à 1 440 minutes, par pas de 15
    CONSTRAINT ck_app_settings_standard_day CHECK (standard_day_minutes BETWEEN 60 AND 1440 AND standard_day_minutes % 15 = 0),
    CONSTRAINT ck_app_settings_last_modification CHECK ((updated_at IS NULL) = (updated_by IS NULL))
);

-- RG-06-01 : à l'installation, la journée standard vaut 420 minutes (7 h)
INSERT INTO app_settings (id, standard_day_minutes) VALUES (1, 420);
