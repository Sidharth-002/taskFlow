package com.flowdesk.team.mapper;

import com.flowdesk.team.dto.TeamResponse;
import com.flowdesk.team.entity.Team;
import org.springframework.stereotype.Component;

/**
 * Like {@code CommentMapper}, this resolves the team lead's *name*, not
 * just their ID - an accepted, bounded cost (the number of teams in an
 * organization is naturally small), because showing who leads a team is
 * basic expected functionality. Member lists are a separate endpoint
 * ({@code GET /api/teams/{id}/members}), not embedded here.
 */
@Component
public class TeamMapper {

    public TeamResponse toResponse(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getOrganization().getId(),
                team.getName(),
                team.getDescription(),
                team.getTeamLead() != null ? team.getTeamLead().getId() : null,
                team.getTeamLead() != null ? team.getTeamLead().getFullName() : null,
                team.isActive(),
                team.getCreatedAt(),
                team.getUpdatedAt());
    }
}
