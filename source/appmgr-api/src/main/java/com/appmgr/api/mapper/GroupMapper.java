package com.appmgr.api.mapper;

import com.appmgr.api.dto.group.GroupDto;
import com.appmgr.api.form.group.CreateGroupForm;
import com.appmgr.api.model.Group;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        uses = {PermissionMapper.class})
public interface GroupMapper {
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "kind", target = "kind")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromCreateGroupFormToEntity")
    Group fromCreateGroupFormToEntity(CreateGroupForm createGroupForm);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "kind", target = "kind")
    @Mapping(source = "permissions",target = "permissions",qualifiedByName = "fromEntityToPermissionDtoList")
    @Named("fromEntityToGroupDto")
    GroupDto fromEntityToGroupDto(Group Group);

    @IterableMapping(elementTargetType = GroupDto.class, qualifiedByName = "fromEntityToGroupDto")
    List<GroupDto> fromEntityListToGroupDtoList(List<Group> Groups);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromGroupToAutoCompleteDto")
    GroupDto fromGroupToAutoCompleteDto(Group Group);

    @IterableMapping(elementTargetType = GroupDto.class, qualifiedByName = "fromGroupToAutoCompleteDto")
    List<GroupDto> convertGroupToAutoCompleteDto(List<Group> Groups);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "kind", target = "kind")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "isSystemRole", target = "isSystemRole")
    @Mapping(source = "createdDate", target = "createdDate")
    @Mapping(source = "modifiedDate", target = "modifiedDate")
    @Mapping(source = "status", target = "status")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromEntityToGroupDtoShort")
    GroupDto fromEntityToGroupDtoShort(Group group);

    @IterableMapping(elementTargetType = GroupDto.class, qualifiedByName = "fromEntityToGroupDtoShort")
    @Named("fromEntityToGroupDtoList")
    List<GroupDto> fromEntityToGroupDtoList(List<Group> groups);
}
