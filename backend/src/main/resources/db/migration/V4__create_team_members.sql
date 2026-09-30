CREATE TABLE team_members
(
    team_id BIGINT NOT NULL REFERENCES teams (id),
    user_id BIGINT NOT NULL REFERENCES users (id),
    PRIMARY KEY (team_id, user_id)
);

CREATE INDEX idx_team_members_user_id ON team_members (user_id);
