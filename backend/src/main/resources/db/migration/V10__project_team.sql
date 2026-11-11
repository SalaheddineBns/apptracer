ALTER TABLE projects ADD COLUMN team_id BIGINT REFERENCES teams (id);

CREATE INDEX ix_projects_team_id ON projects (team_id);
