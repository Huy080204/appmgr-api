package com.appmgr.api.mapper;

import com.appmgr.api.dto.bundle.CheckVersionDto;
import com.appmgr.api.dto.version.VersionDto;
import com.appmgr.api.form.version.CreateVersionForm;
import com.appmgr.api.form.version.UpdateVersionForm;
import com.appmgr.api.model.Version;
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
        uses = {ApplicationMapper.class, CategoryMapper.class, ChannelMapper.class})
public interface VersionMapper {

    @Mapping(source = "type", target = "type")
    @Mapping(source = "versionCode", target = "versionCode")
    @Mapping(source = "versionName", target = "versionName")
    @Mapping(source = "requiredUpdate", target = "requiredUpdate")
    @Mapping(source = "runtimeVersion", target = "runtimeVersion")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromCreateVersionFormToEntity")
    Version fromCreateForm(CreateVersionForm createVersionForm);

    @Mapping(source = "versionCode", target = "versionCode")
    @Mapping(source = "versionName", target = "versionName")
    @Mapping(source = "requiredUpdate", target = "requiredUpdate")
    @BeanMapping(ignoreByDefault = true)
    @Named("updateFromUpdateVersionForm")
    void updateFromUpdateForm(UpdateVersionForm updateVersionForm, @MappingTarget Version version);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "application", target = "application", qualifiedByName = "fromEntityToApplicationDto")
    @Mapping(source = "category", target = "category", qualifiedByName = "fromEntityToCategoryDto")
    @Mapping(source = "channel", target = "channel", qualifiedByName = "adminGetMapping")
    @Mapping(source = "type", target = "type")
    @Mapping(source = "versionCode", target = "versionCode")
    @Mapping(source = "versionName", target = "versionName")
    @Mapping(source = "requiredUpdate", target = "requiredUpdate")
    @Mapping(source = "timestamp", target = "timestamp")
    @Mapping(source = "minVersion", target = "minVersion")
    @Mapping(source = "urlBundle", target = "urlBundle")
    @Mapping(source = "runtimeVersion", target = "runtimeVersion")
    @Mapping(source = "manifestId", target = "manifestId")
    @Mapping(source = "modifiedDate", target = "modifiedDate")
    @Mapping(source = "createdDate", target = "createdDate")
    @Mapping(source = "status", target = "status")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromEntityToVersionDto")
    VersionDto fromEntityToDto(Version version);

    @Mapping(source = "id", target = "id")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromEntityToVersionIdDto")
    VersionDto fromEntityToVersionIdDto(Version version);

    @IterableMapping(elementTargetType = VersionDto.class, qualifiedByName = "fromEntityToVersionDto")
    @Named("fromEntityToVersionDtoList")
    List<VersionDto> fromEntitiesToDtoList(List<Version> versions);

    @Mapping(source = "type", target = "type")
    @Mapping(source = "versionCode", target = "versionCode")
    @Mapping(source = "versionName", target = "versionName")
    @Mapping(source = "minVersion", target = "minVersion")
    @Mapping(source = "requiredUpdate", target = "requiredUpdate")
    @Mapping(source = "urlBundle", target = "urlBundle")
    @BeanMapping(ignoreByDefault = true)
    CheckVersionDto fromEntityToCheckVersionDto(Version version);
}
