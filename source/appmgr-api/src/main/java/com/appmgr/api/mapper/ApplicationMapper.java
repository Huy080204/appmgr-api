package com.appmgr.api.mapper;

import com.appmgr.api.dto.application.ApplicationDto;
import com.appmgr.api.form.application.CreateApplicationForm;
import com.appmgr.api.form.application.UpdateApplicationForm;
import com.appmgr.api.model.Application;
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
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        uses = {ProjectMapper.class})
public interface ApplicationMapper {

    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @BeanMapping(ignoreByDefault = true)
    @Named("adminCreateMapping")
    Application fromFormToEntity(CreateApplicationForm createApplicationForm);

    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @BeanMapping(ignoreByDefault = true)
    @Named("adminUpdateMapping")
    void updateEntityFromForm(UpdateApplicationForm updateApplicationForm, @MappingTarget Application application);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "project", target = "project", qualifiedByName = "fromEntityToProjectDto")
    @Mapping(source = "modifiedDate", target = "modifiedDate")
    @Mapping(source = "createdDate", target = "createdDate")
    @Mapping(source = "status", target = "status")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromEntityToApplicationDto")
    ApplicationDto fromEntityToApplicationDto(Application application);

    @IterableMapping(elementTargetType = ApplicationDto.class, qualifiedByName = "fromEntityToApplicationDto")
    List<ApplicationDto> fromEntityListToApplicationDtoList(List<Application> applications);

    @Mapping(source = "id", target = "id")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromEntityToApplicationIdDto")
    ApplicationDto fromEntityToApplicationIdDto(Application application);
}
