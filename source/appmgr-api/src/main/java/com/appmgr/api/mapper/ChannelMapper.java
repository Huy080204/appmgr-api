package com.appmgr.api.mapper;

import com.appmgr.api.dto.channel.ChannelDto;
import com.appmgr.api.form.channel.CreateChannelForm;
import com.appmgr.api.form.channel.UpdateChannelForm;
import com.appmgr.api.model.Channel;
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
public interface ChannelMapper {

    @Mapping(source = "name", target = "name")
    @BeanMapping(ignoreByDefault = true)
    @Named("adminCreateMapping")
    Channel fromFormToEntity(CreateChannelForm createChannelForm);

    @Mapping(source = "name", target = "name")
    @BeanMapping(ignoreByDefault = true)
    @Named("adminUpdateMapping")
    void updateEntityFromForm(UpdateChannelForm updateChannelForm, @MappingTarget Channel channel);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "modifiedDate", target = "modifiedDate")
    @Mapping(source = "createdDate", target = "createdDate")
    @Mapping(source = "status", target = "status")
    @BeanMapping(ignoreByDefault = true)
    @Named("adminGetMapping")
    ChannelDto fromEntityToAdminDto(Channel channel);

    @IterableMapping(elementTargetType = ChannelDto.class, qualifiedByName = "adminGetMapping")
    List<ChannelDto> fromEntityListToChannelDtoList(List<Channel> channels);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @BeanMapping(ignoreByDefault = true)
    @Named("autoCompleteMapping")
    ChannelDto fromEntityToAdminDtoAutoComplete(Channel channel);

    @IterableMapping(elementTargetType = ChannelDto.class, qualifiedByName = "autoCompleteMapping")
    List<ChannelDto> fromEntityListToChannelDtoAutoComplete(List<Channel> channels);

    @Mapping(source = "id", target = "id")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromEntityToChannelIdDto")
    ChannelDto fromEntityToChannelIdDto(Channel channel);
}
