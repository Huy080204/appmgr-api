package com.appmgr.api.mapper;

import com.appmgr.api.dto.project.ProjectDto;
import com.appmgr.api.form.project.CreateProjectForm;
import com.appmgr.api.form.project.UpdateProjectForm;
import com.appmgr.api.model.Project;
import org.mapstruct.BeanMapping;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProjectMapper {

    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @BeanMapping(ignoreByDefault = true)
    @Named("adminCreateMapping")
    Project fromFormToEntity(CreateProjectForm createProjectForm);

    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @BeanMapping(ignoreByDefault = true)
    @Named("adminUpdateMapping")
    void updateEntityFromForm(UpdateProjectForm updateProjectForm, @MappingTarget Project project);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "modifiedDate", target = "modifiedDate")
    @Mapping(source = "createdDate", target = "createdDate")
    @Mapping(source = "status", target = "status")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromEntityToProjectDto")
    ProjectDto fromEntityToProjectDto(Project project);

    @IterableMapping(elementTargetType = ProjectDto.class, qualifiedByName = "fromEntityToProjectDto")
    List<ProjectDto> fromEntityListToProjectDtoList(List<Project> projects);

    @Mapping(source = "id", target = "id")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromEntityToProjectIdDto")
    ProjectDto fromEntityToProjectIdDto(Project project);
}
