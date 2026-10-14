-- SFD-05 : équipes (RG-05-01, RG-05-02, RG-05-04, RG-05-06, RG-05-07, RG-05-09, RG-05-14, RG-00-04)
CREATE TABLE teams (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- RG-05-01 : 2 à 100 caractères, unicité par l'index ux_teams_name_lower
    name        VARCHAR(100) NOT NULL,
    -- RG-05-02, RG-05-06 : exactement un manager, un même compte peut l'être de plusieurs équipes
    manager_id  BIGINT       NOT NULL REFERENCES users (id),
    version     BIGINT       NOT NULL DEFAULT 0,
    -- RG-05-14, RG-00-04
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  BIGINT       NOT NULL REFERENCES users (id),
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  BIGINT       NOT NULL REFERENCES users (id),
    -- RG-05-09, RG-05-14 : suppression définitive mais tracée (qui, quand)
    deleted_at  TIMESTAMPTZ,
    deleted_by  BIGINT REFERENCES users (id),
    CONSTRAINT ck_teams_name_length CHECK (char_length(name) >= 2),
    CONSTRAINT ck_teams_deletion CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

-- RG-05-01 : nom unique, insensible à la casse, parmi les équipes non supprimées
CREATE UNIQUE INDEX ux_teams_name_lower ON teams (lower(name)) WHERE deleted_at IS NULL;

-- RG-05-10 : équipes d'un manager ; périmètre du MANAGER (SFD-04)
CREATE INDEX ix_teams_manager ON teams (manager_id) WHERE deleted_at IS NULL;

-- RG-05-04 : un compte est membre d'au plus une équipe (clé primaire = le compte)
CREATE TABLE team_members (
    user_id    BIGINT      NOT NULL,
    team_id    BIGINT      NOT NULL REFERENCES teams (id),
    created_at TIMESTAMPTZ NOT NULL,
    created_by BIGINT      NOT NULL REFERENCES users (id),
    CONSTRAINT pk_team_members PRIMARY KEY (user_id),
    CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_team_members_team ON team_members (team_id);
