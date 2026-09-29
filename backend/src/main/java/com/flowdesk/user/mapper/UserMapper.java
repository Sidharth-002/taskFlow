package com.flowdesk.user.mapper;

import com.flowdesk.user.dto.UserSummaryResponse;
import com.flowdesk.user.entity.User;
import org.springframework.stereotype.Component;

/**
 * A plain hand-written mapper rather than MapStruct/ModelMapper: the
 * mapping is small and unlikely to grow complex enough to justify a
 * codegen dependency (Section 43: avoid unnecessary abstractions).
 */
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
