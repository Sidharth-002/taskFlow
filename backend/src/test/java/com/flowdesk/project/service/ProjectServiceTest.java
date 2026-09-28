package com.flowdesk.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.flowdesk.common.exception.ResourceNotFoundException;
import com.flowdesk.common.exception.TenantAccessDeniedException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.project.dto.CreateProjectRequest;
import com.flowdesk.project.dto.UpdateProjectRequest;
import com.flowdesk.project.entity.Project;
import com.flowdesk.project.entity.ProjectStatus;
import com.flowdesk.project.mapper.ProjectMapper;
import com.flowdesk.project.repository.ProjectRepository;
import com.flowdesk.security.AuthenticatedPrincipal;
import com.flowdesk.user.entity.Role;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private OrganizationRepository organizationRepository;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, organizationRepository, new ProjectMapper());
    }

    private void setId(Object entity, Long id) {
        try {
            var field = com.flowdesk.common.entity.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Organization org(Long id) {
        Organization o = Organization.builder().name("Acme").build();
        setId(o, id);
        return o;
    }

    private Project project(Long id, Long orgId) {
        Project p = Project.builder().organization(org(orgId)).name("Website").build();
        setId(p, id);
        return p;
    }

    private AuthenticatedPrincipal caller() {
        return new AuthenticatedPrincipal(1L, ORG_ID, "admin@acme.test", Role.ORG_ADMIN);
    }

    @Test
    void create_scopesProjectToCallerOrganization() {
        when(organizationRepository.getReferenceById(ORG_ID)).thenReturn(org(ORG_ID));
        when(projectRepository.save(any())).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            setId(p, 10L);
            return p;
        });

        var response = projectService.create(new CreateProjectRequest("Website", "desc"), caller());

        assertThat(response.organizationId()).isEqualTo(ORG_ID);
        assertThat(response.status()).isEqualTo(ProjectStatus.ACTIVE);
    }

    @Test
    void getById_projectInDifferentOrganization_throwsTenantAccessDenied() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project(10L, 999L)));

        assertThatThrownBy(() -> projectService.getById(10L, caller()))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void getById_unknownProject_throwsResourceNotFound() {
        when(projectRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(10L, caller()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_appliesNameAndDescription() {
        Project project = project(10L, ORG_ID);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = projectService.update(10L, new UpdateProjectRequest("New name", "New desc"), caller());

        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.description()).isEqualTo("New desc");
    }

    @Test
    void archive_setsStatusToArchived() {
        Project project = project(10L, ORG_ID);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = projectService.archive(10L, caller());

        assertThat(response.status()).isEqualTo(ProjectStatus.ARCHIVED);
    }
}
