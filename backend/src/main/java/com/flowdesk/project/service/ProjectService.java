package com.flowdesk.project.service;

import static com.flowdesk.config.CacheConfig.PROJECTS_CACHE;

import com.flowdesk.shared.exception.ResourceNotFoundException;
import com.flowdesk.shared.exception.TenantAccessDeniedException;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.project.dto.CreateProjectRequest;
import com.flowdesk.project.dto.ProjectResponse;
import com.flowdesk.project.dto.UpdateProjectRequest;
import com.flowdesk.project.entity.Project;
import com.flowdesk.project.entity.ProjectStatus;
import com.flowdesk.project.mapper.ProjectMapper;
import com.flowdesk.project.repository.ProjectRepository;
import com.flowdesk.security.AuthenticatedPrincipal;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final ProjectMapper projectMapper;

    public ProjectService(
            ProjectRepository projectRepository,
            OrganizationRepository organizationRepository,
            ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.organizationRepository = organizationRepository;
        this.projectMapper = projectMapper;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, AuthenticatedPrincipal caller) {
        Organization organization = organizationRepository.getReferenceById(caller.organizationId());
        Project project = Project.builder()
                .organization(organization)
                .name(request.name())
                .description(request.description())
                .build();
        return projectMapper.toResponse(projectRepository.save(project));
    }

    @Cacheable(cacheNames = PROJECTS_CACHE, key = "#caller.organizationId() + ':' + #id")
    @Transactional(readOnly = true)
    public ProjectResponse getById(Long id, AuthenticatedPrincipal caller) {
        return projectMapper.toResponse(loadTenantScoped(id, caller));
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(Pageable pageable, AuthenticatedPrincipal caller) {
        return projectRepository.findByOrganizationId(caller.organizationId(), pageable)
                .map(projectMapper::toResponse);
    }

    @CacheEvict(cacheNames = PROJECTS_CACHE, key = "#caller.organizationId() + ':' + #id")
    @Transactional
    public ProjectResponse update(Long id, UpdateProjectRequest request, AuthenticatedPrincipal caller) {
        Project project = loadTenantScoped(id, caller);
        project.setName(request.name());
        project.setDescription(request.description());
        return projectMapper.toResponse(projectRepository.saveAndFlush(project));
    }

    @CacheEvict(cacheNames = PROJECTS_CACHE, key = "#caller.organizationId() + ':' + #id")
    @Transactional
    public ProjectResponse archive(Long id, AuthenticatedPrincipal caller) {
        Project project = loadTenantScoped(id, caller);
        project.setStatus(ProjectStatus.ARCHIVED);
        return projectMapper.toResponse(projectRepository.saveAndFlush(project));
    }

    private Project loadTenantScoped(Long id, AuthenticatedPrincipal caller) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", id));
        if (!project.getOrganization().getId().equals(caller.organizationId())) {
            throw new TenantAccessDeniedException(
                    "Project %d does not belong to organization %d".formatted(id, caller.organizationId()));
        }
        return project;
    }
}
