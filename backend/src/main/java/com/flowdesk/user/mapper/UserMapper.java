package com.flowdesk.user.mapper;

import com.flowdesk.user.dto.UserSummaryResponse;
import com.flowdesk.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getOrganization() != null ? user.getOrganization().getId() : null,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isActive());
    }
}
