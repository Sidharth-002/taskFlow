-- Join table backing Team.members (@ManyToMany). No attributes of its own
-- (no joined-at timestamp, no per-team role), so a plain composite-PK join
-- table is used rather than a dedicated entity.
CREATE TABLE team_members
(
    team_id BIGINT NOT NULL REFERENCES teams (id),
    user_id BIGINT NOT NULL REFERENCES users (id),
    PRIMARY KEY (team_id, user_id)
);

-- The PK above covers "members of team X" lookups (team_id is the leading
-- column). "teams for user Y" queries the other direction, which needs
-- its own index since it can't use the composite PK efficiently.
CREATE INDEX idx_team_members_user_id ON team_members (user_id);
