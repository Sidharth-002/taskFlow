package com.flowdesk.team.mapper;

import com.flowdesk.team.dto.TeamResponse;
import com.flowdesk.team.entity.Team;
import org.springframework.stereotype.Component;

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
