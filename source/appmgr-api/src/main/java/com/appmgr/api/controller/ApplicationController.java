package com.appmgr.api.controller;

import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.ResponseListDto;
import com.appmgr.api.dto.application.ApplicationDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.form.application.CreateApplicationForm;
import com.appmgr.api.form.application.UpdateApplicationForm;
import com.appmgr.api.mapper.ApplicationMapper;
import com.appmgr.api.model.Application;
import com.appmgr.api.model.Project;
import com.appmgr.api.model.criteria.ApplicationCriteria;
import com.appmgr.api.repository.ApplicationRepository;
import com.appmgr.api.repository.ProjectRepository;
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
@RequestMapping("/v1/application")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class ApplicationController extends ABasicController {
    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ApplicationMapper applicationMapper;

    @GetMapping(value = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('APP_V')")
    public ApiMessageDto<ApplicationDto> get(@PathVariable Long id) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Not found application!", ErrorCode.APPLICATION_ERROR_NOT_FOUND));
        return makeSuccessResponse(applicationMapper.fromEntityToApplicationDto(application), "Get application success");
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('APP_L')")
    public ApiMessageDto<ResponseListDto<List<ApplicationDto>>> list(ApplicationCriteria applicationCriteria, @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Application> page = applicationRepository.findAll(applicationCriteria.getCriteria(), pageable);
        ResponseListDto<List<ApplicationDto>> responseListDto =
                makeResponseListDto(page, applicationMapper::fromEntityListToApplicationDtoList);
        return makeSuccessResponse(responseListDto, "Get list success");
    }

    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('APP_C')")
    @Transactional
    public ApiMessageDto<ApplicationDto> create(@Valid @RequestBody CreateApplicationForm createApplicationForm, BindingResult bindingResult) {
        Project project = projectRepository.findById(createApplicationForm.getProjectId())
                .orElseThrow(() -> new NotFoundException("Not found project!", ErrorCode.PROJECT_ERROR_NOT_FOUND));
        if (applicationRepository.existsByNameAndProjectId(createApplicationForm.getName(), project.getId())) {
            throw new BadRequestException("Application name already exist", ErrorCode.APPLICATION_ERROR_NAME_EXIST);
        }

        Application application = applicationMapper.fromFormToEntity(createApplicationForm);
        application.setProject(project);
        applicationRepository.save(application);
        return makeSuccessResponse(applicationMapper.fromEntityToApplicationIdDto(application), "Create application success");
    }

    @PutMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('APP_U')")
    @Transactional
    public ApiMessageDto<Void> update(@Valid @RequestBody UpdateApplicationForm updateApplicationForm, BindingResult bindingResult) {
        Application application = applicationRepository.findById(updateApplicationForm.getId())
                .orElseThrow(() -> new NotFoundException("Not found application!", ErrorCode.APPLICATION_ERROR_NOT_FOUND));

        if (!Objects.equals(application.getName(), updateApplicationForm.getName())
                && applicationRepository.existsByNameAndProjectIdAndIdNot(
                updateApplicationForm.getName(), application.getProject().getId(), application.getId())) {
            throw new BadRequestException("Application name already exist", ErrorCode.APPLICATION_ERROR_NAME_EXIST);
        }

        applicationMapper.updateEntityFromForm(updateApplicationForm, application);
        applicationRepository.save(application);
        return makeSuccessResponse("Update application success");
    }

    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('APP_D')")
    @Transactional
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Not found application!", ErrorCode.APPLICATION_ERROR_NOT_FOUND));
        applicationRepository.delete(application);
        return makeSuccessResponse("Delete application success");
    }
}
