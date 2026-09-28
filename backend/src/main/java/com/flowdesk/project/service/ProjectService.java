package com.flowdesk.project.service;

import static com.flowdesk.config.CacheConfig.PROJECTS_CACHE;

import com.flowdesk.common.exception.ResourceNotFoundException;
import com.flowdesk.common.exception.TenantAccessDeniedException;
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

/**
 * Every method here is only ever called for the four organization-scoped
 * roles ({@code @PreAuthorize} in {@code ProjectController} excludes
 * {@code SUPER_ADMIN}), so {@code caller.organizationId()} is always
 * non-null by the time it reaches this class.
 *
 * <p>{@code getById} is cached (Phase 7, see {@code CacheConfig}) - a
 * single project is read far more often (every ticket create/update
 * references one) than it's written. The cache key includes
 * {@code organizationId} alongside {@code id}, even though ticket IDs are
 * already globally unique, specifically so a cross-organization
 * {@code TenantAccessDeniedException} is never served from - or, worse,
 * masks - another organization's cached entry. {@code list} stays
 * uncached: it's already pagination-limited and cheap, and caching a
 * whole page per (organization, page, sort) combination would fragment
 * the cache for little benefit.
 */
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
        // saveAndFlush, not save: @LastModifiedDate is only applied by
        // Hibernate's auditing listener at flush time, so mapping before
        // flushing would return the *previous* updatedAt instead of the
        // value this update actually produced.
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
