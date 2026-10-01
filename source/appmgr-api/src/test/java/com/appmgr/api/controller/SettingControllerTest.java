package com.appmgr.api.controller;

import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.ResponseListDto;
import com.appmgr.api.dto.setting.SettingDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.form.setting.CreateSettingForm;
import com.appmgr.api.form.setting.FindByGroupNameForm;
import com.appmgr.api.form.setting.FindByKeyNameForm;
import com.appmgr.api.form.setting.UpdateSettingForm;
import com.appmgr.api.mapper.SettingMapper;
import com.appmgr.api.model.Setting;
import com.appmgr.api.model.criteria.SettingCriteria;
import com.appmgr.api.repository.SettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingControllerTest {

    @Mock private SettingRepository settingRepository;
    @Mock private SettingMapper settingMapper;
    @InjectMocks private SettingController controller;

    @Test
    void shouldThrowNotFoundWhenSettingIdDoesNotExist() {
        when(settingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(1L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SETTING_ERROR_NOT_FOUND);
    }

    @Test
    void shouldReturnSettingDtoWhenIdExists() {
        Setting entity = new Setting();
        SettingDto dto = new SettingDto();
        when(settingRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(settingMapper.fromEntityToSettingDto(entity)).thenReturn(dto);

        ApiMessageDto<SettingDto> result = controller.get(1L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData()).isSameAs(dto);
        assertThat(result.getMessage()).isEqualTo("Get setting success");
        verify(settingRepository).findById(1L);
    }

    @Test
    void shouldReturnSettingListWhenListCalled() {
        SettingCriteria criteria = new SettingCriteria();
        Pageable pageable = PageRequest.of(0, 10);
        Setting entity = new Setting();
        Page<Setting> page = new PageImpl<>(Collections.singletonList(entity));
        List<SettingDto> dtoList = Collections.singletonList(new SettingDto());
        when(settingRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(settingMapper.fromEntityToSettingDtoList(anyListOfSetting())).thenReturn(dtoList);

        ApiMessageDto<ResponseListDto<List<SettingDto>>> result = controller.list(criteria, pageable);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Get list setting success");
        assertThat(result.getData().getContent()).isSameAs(dtoList);
        assertThat(result.getData().getTotalElements()).isEqualTo(1);
        assertThat(result.getData().getTotalPages()).isEqualTo(1);
    }

    @Test
    void shouldReturnAutoCompleteListWhenAutoCompleteCalled() {
        SettingCriteria criteria = new SettingCriteria();
        Pageable pageable = PageRequest.of(0, 10);
        Setting entity = new Setting();
        Page<Setting> page = new PageImpl<>(Collections.singletonList(entity));
        List<SettingDto> dtoList = Collections.singletonList(new SettingDto());
        when(settingRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(settingMapper.fromEntityToSettingDtoAutoCompleteList(anyListOfSetting())).thenReturn(dtoList);

        ApiMessageDto<ResponseListDto<List<SettingDto>>> result = controller.autoComplete(criteria, pageable);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Get list setting success");
        assertThat(result.getData().getContent()).isSameAs(dtoList);
    }

    @Test
    void shouldThrowBadRequestWhenCreateSettingWithExistedGroupNameAndKeyName() {
        CreateSettingForm form = new CreateSettingForm();
        form.setGroupName("group");
        form.setKeyName("key");
        when(settingRepository.findFirstByGroupNameAndKeyName("group", "key")).thenReturn(Optional.of(new Setting()));

        assertThatThrownBy(() -> controller.create(form, null))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SETTING_ERROR_EXISTED_GROUP_NAME_AND_KEY_NAME);
    }

    @Test
    void shouldCreateSettingWhenGroupNameAndKeyNameNotExisted() {
        CreateSettingForm form = new CreateSettingForm();
        form.setGroupName("group");
        form.setKeyName("key");
        Setting entity = new Setting();
        when(settingRepository.findFirstByGroupNameAndKeyName("group", "key")).thenReturn(Optional.empty());
        when(settingMapper.fromCreateSettingFormToEntity(form)).thenReturn(entity);

        ApiMessageDto<Void> result = controller.create(form, null);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Create setting success");
        verify(settingRepository).save(entity);
    }

    @Test
    void shouldThrowNotFoundWhenUpdateSettingIdDoesNotExist() {
        UpdateSettingForm form = new UpdateSettingForm();
        form.setId(1L);
        when(settingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(form, null))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SETTING_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowBadRequestWhenUpdateSettingWithExistedGroupNameAndKeyName() {
        UpdateSettingForm form = new UpdateSettingForm();
        form.setId(1L);
        form.setGroupName("newGroup");
        form.setKeyName("newKey");
        Setting setting = new Setting();
        setting.setGroupName("oldGroup");
        setting.setKeyName("oldKey");
        when(settingRepository.findById(1L)).thenReturn(Optional.of(setting));
        when(settingRepository.findFirstByGroupNameAndKeyName("newGroup", "newKey")).thenReturn(Optional.of(new Setting()));

        assertThatThrownBy(() -> controller.update(form, null))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.SETTING_ERROR_EXISTED_GROUP_NAME_AND_KEY_NAME);
    }

    @Test
    void shouldUpdateSettingWhenGroupNameAndKeyNameNotChanged() {
        UpdateSettingForm form = new UpdateSettingForm();
        form.setId(1L);
        form.setGroupName("group");
        form.setKeyName("key");
        Setting setting = new Setting();
        setting.setGroupName("group");
        setting.setKeyName("key");
        when(settingRepository.findById(1L)).thenReturn(Optional.of(setting));
        doAnswer(invocation -> {
            UpdateSettingForm f = invocation.getArgument(0);
            Setting e = invocation.getArgument(1);
            e.setGroupName(f.getGroupName());
            e.setKeyName(f.getKeyName());
            return null;
        }).when(settingMapper).fromUpdateSettingFormToEntity(any(UpdateSettingForm.class), any(Setting.class));

        ApiMessageDto<Void> result = controller.update(form, null);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Update setting success");
        verify(settingRepository).save(setting);
    }

    @Test
    void shouldReturnSettingListWhenFindByKeyCalled() {
        FindByKeyNameForm form = new FindByKeyNameForm();
        form.setKeyNames(new String[]{"key"});
        List<Setting> settings = Collections.singletonList(new Setting());
        List<SettingDto> dtoList = Collections.singletonList(new SettingDto());
        when(settingRepository.findByKeyNames(form.getKeyNames(), false)).thenReturn(settings);
        when(settingMapper.fromEntityToSettingDtoList(settings)).thenReturn(dtoList);

        ApiMessageDto<List<SettingDto>> result = controller.findByKey(form);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData()).isSameAs(dtoList);
        assertThat(result.getMessage()).isEqualTo("Find key name success");
    }

    @Test
    void shouldReturnSettingListWhenFindByGroupCalled() {
        FindByGroupNameForm form = new FindByGroupNameForm();
        form.setGroupNames(new String[]{"group"});
        List<Setting> settings = Collections.singletonList(new Setting());
        List<SettingDto> dtoList = Collections.singletonList(new SettingDto());
        when(settingRepository.findByGroupNames(form.getGroupNames(), false)).thenReturn(settings);
        when(settingMapper.fromEntityToSettingDtoList(settings)).thenReturn(dtoList);

        ApiMessageDto<List<SettingDto>> result = controller.findByGroup(form);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData()).isSameAs(dtoList);
        assertThat(result.getMessage()).isEqualTo("Find group name success");
    }

    @Test
    void shouldReturnPublicSettingListWhenListSettingCalled() {
        List<Setting> settings = Collections.singletonList(new Setting());
        List<SettingDto> dtoList = Collections.singletonList(new SettingDto());
        when(settingRepository.findAllByIsSystem(false)).thenReturn(settings);
        when(settingMapper.fromEntityToSettingDtoPublicList(settings)).thenReturn(dtoList);

        ApiMessageDto<List<SettingDto>> result = controller.listSetting();

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData()).isSameAs(dtoList);
        assertThat(result.getMessage()).isEqualTo("Get list setting success");
    }

    @SuppressWarnings("unchecked")
    private static List<Setting> anyListOfSetting() {
        return any(List.class);
    }
}
