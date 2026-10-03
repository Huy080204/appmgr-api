package com.appmgr.api.controller;

import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.ResponseListDto;
import com.appmgr.api.dto.project.ProjectDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.form.project.CreateProjectForm;
import com.appmgr.api.form.project.UpdateProjectForm;
import com.appmgr.api.mapper.ProjectMapper;
import com.appmgr.api.model.Application;
import com.appmgr.api.model.Project;
import com.appmgr.api.model.criteria.ProjectCriteria;
import com.appmgr.api.repository.ApplicationRepository;
import com.appmgr.api.repository.ProjectRepository;
import com.appmgr.api.repository.VersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/v1/project")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class ProjectController extends ABasicController {
    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private VersionRepository versionRepository;

    @GetMapping(value = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('PRO_V')")
    public ApiMessageDto<ProjectDto> get(@PathVariable Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Not found project!", ErrorCode.PROJECT_ERROR_NOT_FOUND));
        return makeSuccessResponse(projectMapper.fromEntityToProjectDto(project), "Get project success");
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('PRO_L')")
    public ApiMessageDto<ResponseListDto<List<ProjectDto>>> list(ProjectCriteria projectCriteria, @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Project> page = projectRepository.findAll(projectCriteria.getCriteria(), pageable);
        ResponseListDto<List<ProjectDto>> responseListDto =
                makeResponseListDto(page, projectMapper::fromEntityListToProjectDtoList);
        return makeSuccessResponse(responseListDto, "Get list success");
    }

    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('PRO_C')")
    @Transactional
    public ApiMessageDto<ProjectDto> create(@Valid @RequestBody CreateProjectForm createProjectForm, BindingResult bindingResult) {
        if (projectRepository.existsByName(createProjectForm.getName())) {
            throw new BadRequestException("Project name already exist", ErrorCode.PROJECT_ERROR_NAME_EXIST);
        }

        Project project = projectMapper.fromFormToEntity(createProjectForm);
        projectRepository.save(project);
        return makeSuccessResponse(projectMapper.fromEntityToProjectIdDto(project), "Create project success");
    }

    @PutMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('PRO_U')")
    @Transactional
    public ApiMessageDto<Void> update(@Valid @RequestBody UpdateProjectForm updateProjectForm, BindingResult bindingResult) {
        Project project = projectRepository.findById(updateProjectForm.getId())
                .orElseThrow(() -> new NotFoundException("Not found project!", ErrorCode.PROJECT_ERROR_NOT_FOUND));

        if (!Objects.equals(project.getName(), updateProjectForm.getName())
                && projectRepository.existsByNameAndIdNot(updateProjectForm.getName(), project.getId())) {
            throw new BadRequestException("Project name already exist", ErrorCode.PROJECT_ERROR_NAME_EXIST);
        }
        projectMapper.updateEntityFromForm(updateProjectForm, project);
        projectRepository.save(project);
        return makeSuccessResponse("Update project success");
    }

    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('PRO_D')")
    @Transactional
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Not found project!", ErrorCode.PROJECT_ERROR_NOT_FOUND));
        versionRepository.deleteAllByApplicationProjectId(id);
        applicationRepository.deleteAllByProjectId(id);
        projectRepository.delete(project);
        return makeSuccessResponse("Delete project success");
    }
}
