package com.appmgr.api.controller;

import com.appmgr.api.constant.BaseConstant;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/v1/setting")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class SettingController extends ABasicController {
    @Autowired
    private SettingRepository settingRepository;
    @Autowired
    private SettingMapper settingMapper;

    @GetMapping(value = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SET_V')")
    public ApiMessageDto<SettingDto> get(@PathVariable("id") Long id) {
        Setting setting = settingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Not found setting", ErrorCode.SETTING_ERROR_NOT_FOUND));
        return makeSuccessResponse(settingMapper.fromEntityToSettingDto(setting), "Get setting success");
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SET_L')")
    public ApiMessageDto<ResponseListDto<List<SettingDto>>> list(SettingCriteria settingCriteria, Pageable pageable) {
        Page<Setting> settings = settingRepository.findAll(settingCriteria.getCriteria(), pageable);
        return makeSuccessResponse(makeResponseListDto(settings, settingMapper::fromEntityToSettingDtoList), "Get list setting success");
    }

    @GetMapping(value = "/auto-complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiMessageDto<ResponseListDto<List<SettingDto>>> autoComplete(SettingCriteria settingCriteria, Pageable pageable) {
        settingCriteria.setStatus(BaseConstant.STATUS_ACTIVE);
        Page<Setting> settings = settingRepository.findAll(settingCriteria.getCriteria(), pageable);
        return makeSuccessResponse(makeResponseListDto(settings, settingMapper::fromEntityToSettingDtoAutoCompleteList), "Get list setting success");
    }

    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SET_C')")
    @Transactional
    public ApiMessageDto<Void> create(@Valid @RequestBody CreateSettingForm createSettingForm, BindingResult bindingResult) {
        if (settingRepository.findFirstByGroupNameAndKeyName(createSettingForm.getGroupName(), createSettingForm.getKeyName()).isPresent()) {
            throw new BadRequestException("Group name and key name existed", ErrorCode.SETTING_ERROR_EXISTED_GROUP_NAME_AND_KEY_NAME);
        }
        settingRepository.save(settingMapper.fromCreateSettingFormToEntity(createSettingForm));
        return makeSuccessResponse("Create setting success");
    }

    @PutMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SET_U')")
    @Transactional
    public ApiMessageDto<Void> update(@Valid @RequestBody UpdateSettingForm updateSettingForm, BindingResult bindingResult) {
        Setting setting = settingRepository.findById(updateSettingForm.getId())
                .orElseThrow(() -> new NotFoundException("Not found setting", ErrorCode.SETTING_ERROR_NOT_FOUND));
        if ((!updateSettingForm.getGroupName().equals(setting.getGroupName()) || !updateSettingForm.getKeyName().equals(setting.getKeyName())) &&
                settingRepository.findFirstByGroupNameAndKeyName(updateSettingForm.getGroupName(), updateSettingForm.getKeyName()).isPresent()) {
            throw new BadRequestException("Group name and key name existed", ErrorCode.SETTING_ERROR_EXISTED_GROUP_NAME_AND_KEY_NAME);
        }
        settingMapper.fromUpdateSettingFormToEntity(updateSettingForm, setting);
        settingRepository.save(setting);
        return makeSuccessResponse("Update setting success");
    }

    @GetMapping(value = "/find-by-key", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiMessageDto<List<SettingDto>> findByKey(FindByKeyNameForm findByKeyNameForm) {
        List<Setting> settings = settingRepository.findByKeyNames(findByKeyNameForm.getKeyNames(), false);
        return makeSuccessResponse(settingMapper.fromEntityToSettingDtoList(settings), "Find key name success");
    }

    @GetMapping(value = "/find-by-group", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiMessageDto<List<SettingDto>> findByGroup(FindByGroupNameForm findByGroupNameForm) {
        List<Setting> settings = settingRepository.findByGroupNames(findByGroupNameForm.getGroupNames(), false);
        return makeSuccessResponse(settingMapper.fromEntityToSettingDtoList(settings), "Find group name success");
    }

    @GetMapping(value = "/public", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiMessageDto<List<SettingDto>> listSetting() {
        List<Setting> settings = settingRepository.findAllByIsSystem(false);
        return makeSuccessResponse(settingMapper.fromEntityToSettingDtoPublicList(settings), "Get list setting success");
    }
}
