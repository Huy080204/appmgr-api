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
import com.appmgr.api.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelControllerTest {

    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private UserServiceImpl userService;
    @Spy
    private ChannelMapper channelMapper = Mappers.getMapper(ChannelMapper.class);
    @InjectMocks
    private ChannelController controller;

    @Test
    void shouldCreateChannelWhenNameNotExisted() {
        CreateChannelForm form = new CreateChannelForm();
        form.setName("Beta");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(channelRepository.existsByName("Beta")).thenReturn(false);

        ApiMessageDto<ChannelDto> result = controller.create(form, bindingResult);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Create channel success");
        verify(channelRepository).save(any(Channel.class));
    }

    @Test
    void shouldThrowBadRequestWhenCreateNameExisted() {
        CreateChannelForm form = new CreateChannelForm();
        form.setName("Beta");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(channelRepository.existsByName("Beta")).thenReturn(true);

        assertThatThrownBy(() -> controller.create(form, bindingResult))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_NAME_EXISTED);
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    void shouldUpdateChannelWhenNewNameNotExisted() {
        Channel entity = new Channel();
        entity.setId(1L);
        entity.setName("Old");
        UpdateChannelForm form = new UpdateChannelForm();
        form.setId(1L);
        form.setName("New");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(channelRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(channelRepository.existsByNameAndIdNot("New", 1L)).thenReturn(false);

        ApiMessageDto<Void> result = controller.update(form, bindingResult);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Update channel success");
        assertThat(entity.getName()).isEqualTo("New");
        verify(channelRepository).save(entity);
    }

    @Test
    void shouldThrowBadRequestWhenUpdateChangedNameExisted() {
        Channel entity = new Channel();
        entity.setId(1L);
        entity.setName("Old");
        UpdateChannelForm form = new UpdateChannelForm();
        form.setId(1L);
        form.setName("Taken");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(channelRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(channelRepository.existsByNameAndIdNot("Taken", 1L)).thenReturn(true);

        assertThatThrownBy(() -> controller.update(form, bindingResult))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_NAME_EXISTED);
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    void shouldSkipUniquenessCheckWhenUpdateKeepsSameName() {
        Channel entity = new Channel();
        entity.setId(1L);
        entity.setName("Same");
        UpdateChannelForm form = new UpdateChannelForm();
        form.setId(1L);
        form.setName("Same");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(channelRepository.findById(1L)).thenReturn(Optional.of(entity));

        ApiMessageDto<Void> result = controller.update(form, bindingResult);

        assertThat(result.getResult()).isTrue();
        verify(channelRepository, never()).existsByNameAndIdNot(any(), any());
        verify(channelRepository).save(entity);
    }

    @Test
    void shouldThrowNotFoundWhenUpdateIdDoesNotExist() {
        UpdateChannelForm form = new UpdateChannelForm();
        form.setId(1L);
        form.setName("New");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        when(channelRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(form, bindingResult))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_NOT_FOUND);
    }

    @Test
    void shouldDeleteChannelWhenIdExists() {
        Channel entity = new Channel();
        entity.setId(1L);
        when(channelRepository.findById(1L)).thenReturn(Optional.of(entity));

        ApiMessageDto<Void> result = controller.delete(1L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Delete channel success");
        verify(channelRepository).delete(entity);
    }

    @Test
    void shouldThrowNotFoundWhenDeleteIdDoesNotExist() {
        when(channelRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.delete(1L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_NOT_FOUND);
        verify(channelRepository, never()).delete(any(Channel.class));
    }

    @Test
    void shouldReturnChannelWhenIdExists() {
        Channel entity = new Channel();
        entity.setId(1L);
        entity.setName("Beta");
        when(channelRepository.findById(1L)).thenReturn(Optional.of(entity));

        ApiMessageDto<ChannelDto> result = controller.get(1L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(1L);
        assertThat(result.getData().getName()).isEqualTo("Beta");
        assertThat(result.getMessage()).isEqualTo("Get channel success");
    }

    @Test
    void shouldThrowNotFoundWhenGetIdDoesNotExist() {
        when(channelRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(1L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_NOT_FOUND);
    }

    @Test
    void shouldDefaultStatusActiveWhenAutoComplete() {
        Channel entity = new Channel();
        entity.setId(1L);
        entity.setName("Beta");
        Page<Channel> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1);
        when(channelRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        ChannelCriteria criteria = new ChannelCriteria();

        ApiMessageDto<ResponseListDto<List<ChannelDto>>> result =
                controller.autoComplete(criteria, PageRequest.of(0, 10));

        assertThat(criteria.getStatus()).isEqualTo(BaseConstant.STATUS_ACTIVE);
        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getContent()).hasSize(1);
        assertThat(result.getData().getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getData().getTotalElements()).isEqualTo(1L);
        assertThat(result.getMessage()).isEqualTo("Get auto complete channels success");
    }
}
