package com.appmgr.api.mapper;

import com.appmgr.api.dto.setting.SettingDto;
import com.appmgr.api.form.setting.CreateSettingForm;
import com.appmgr.api.form.setting.UpdateSettingForm;
import com.appmgr.api.model.Setting;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SettingMapper {
    @Mapping(source = "groupName", target = "groupName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "keyName", target = "keyName")
    @Mapping(source = "valueData", target = "valueData")
    @Mapping(source = "dataType", target = "dataType")
    @Mapping(source = "option", target = "option")
    @Mapping(source = "isSystem", target = "isSystem")
    @BeanMapping(ignoreByDefault = true)
    Setting fromCreateSettingFormToEntity(CreateSettingForm createSettingForm);

    @Mapping(source = "valueData", target = "valueData")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "groupName", target = "groupName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "keyName", target = "keyName")
    @Mapping(source = "dataType", target = "dataType")
    @Mapping(source = "option", target = "option")
    @BeanMapping(ignoreByDefault = true)
    void fromUpdateSettingFormToEntity(UpdateSettingForm updateSettingForm, @MappingTarget Setting setting);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "groupName", target = "groupName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "keyName", target = "keyName")
    @Mapping(source = "valueData", target = "valueData")
    @Mapping(source = "dataType", target = "dataType")
    @Mapping(source = "option", target = "option")
    @Mapping(source = "isSystem", target = "isSystem")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "createdDate", target = "createdDate")
    @Mapping(source = "modifiedDate", target = "modifiedDate")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromEntityToSettingDto")
    SettingDto fromEntityToSettingDto(Setting setting);

    @IterableMapping(elementTargetType = SettingDto.class, qualifiedByName = "fromEntityToSettingDto")
    List<SettingDto> fromEntityToSettingDtoList(List<Setting> settings);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "groupName", target = "groupName")
    @Mapping(source = "keyName", target = "keyName")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromEntityToSettingDtoAutoComplete")
    SettingDto fromEntityToSettingDtoAutoComplete(Setting setting);

    @IterableMapping(elementTargetType = SettingDto.class, qualifiedByName = "fromEntityToSettingDtoAutoComplete")
    List<SettingDto> fromEntityToSettingDtoAutoCompleteList(List<Setting> settings);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "groupName", target = "groupName")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "keyName", target = "keyName")
    @Mapping(source = "valueData", target = "valueData")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromEntityToSettingDtoPublic")
    SettingDto fromEntityToSettingDtoPublic(Setting setting);

    @IterableMapping(elementTargetType = SettingDto.class, qualifiedByName = "fromEntityToSettingDtoPublic")
    List<SettingDto> fromEntityToSettingDtoPublicList(List<Setting> settings);
}
