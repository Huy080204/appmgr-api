package com.appmgr.api.controller;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.ResponseListDto;
import com.appmgr.api.dto.channel.ChannelDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.form.channel.CreateChannelForm;
import com.appmgr.api.form.channel.UpdateChannelForm;
import com.appmgr.api.mapper.ChannelMapper;
import com.appmgr.api.model.Channel;
import com.appmgr.api.model.criteria.ChannelCriteria;
import com.appmgr.api.repository.ChannelRepository;
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
@RequestMapping("/v1/channel")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class ChannelController extends ABasicController {
    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ChannelMapper channelMapper;

    @GetMapping(value = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CHN_V')")
    public ApiMessageDto<ChannelDto> get(@PathVariable Long id) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Not found channel!", ErrorCode.CHANNEL_ERROR_NOT_FOUND));
        return makeSuccessResponse(channelMapper.fromEntityToAdminDto(channel), "Get channel success");
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CHN_L')")
    public ApiMessageDto<ResponseListDto<List<ChannelDto>>> list(ChannelCriteria channelCriteria, @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Channel> page = channelRepository.findAll(channelCriteria.getCriteria(), pageable);
        ResponseListDto<List<ChannelDto>> responseListDto =
                makeResponseListDto(page, channelMapper::fromEntityListToChannelDtoList);
        return makeSuccessResponse(responseListDto, "Get list success");
    }

    @GetMapping(value = "/auto-complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiMessageDto<ResponseListDto<List<ChannelDto>>> autoComplete(ChannelCriteria channelCriteria, Pageable pageable) {
        channelCriteria.setStatus(BaseConstant.STATUS_ACTIVE);
        Page<Channel> page = channelRepository.findAll(channelCriteria.getCriteria(), pageable);
        ResponseListDto<List<ChannelDto>> responseListDto =
                makeResponseListDto(page, channelMapper::fromEntityListToChannelDtoAutoComplete);
        return makeSuccessResponse(responseListDto, "Get auto complete channels success");
    }

    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CHN_C')")
    @Transactional
    public ApiMessageDto<ChannelDto> create(@Valid @RequestBody CreateChannelForm createChannelForm, BindingResult bindingResult) {
        if (channelRepository.existsByName(createChannelForm.getName())) {
            throw new BadRequestException("Channel name already existed", ErrorCode.CHANNEL_ERROR_NAME_EXISTED);
        }
        Channel channel = channelMapper.fromFormToEntity(createChannelForm);
        channelRepository.save(channel);
        return makeSuccessResponse(channelMapper.fromEntityToChannelIdDto(channel), "Create channel success");
    }

    @PutMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CHN_U')")
    @Transactional
    public ApiMessageDto<Void> update(@Valid @RequestBody UpdateChannelForm updateChannelForm, BindingResult bindingResult) {
        Channel channel = channelRepository.findById(updateChannelForm.getId())
                .orElseThrow(() -> new NotFoundException("Not found channel!", ErrorCode.CHANNEL_ERROR_NOT_FOUND));
        if (!Objects.equals(channel.getName(), updateChannelForm.getName())
                && channelRepository.existsByNameAndIdNot(updateChannelForm.getName(), channel.getId())) {
            throw new BadRequestException("Channel name already existed", ErrorCode.CHANNEL_ERROR_NAME_EXISTED);
        }
        channelMapper.updateEntityFromForm(updateChannelForm, channel);
        channelRepository.save(channel);
        return makeSuccessResponse("Update channel success");
    }

    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CHN_D')")
    @Transactional
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Not found channel!", ErrorCode.CHANNEL_ERROR_NOT_FOUND));
        channelRepository.delete(channel);
        return makeSuccessResponse("Delete channel success");
    }
}
