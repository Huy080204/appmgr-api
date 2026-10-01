package com.appmgr.api.controller;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.ResponseListDto;
import com.appmgr.api.dto.group.GroupDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.exception.UnauthorizationException;
import com.appmgr.api.form.group.CreateGroupForm;
import com.appmgr.api.form.group.UpdateGroupForm;
import com.appmgr.api.mapper.GroupMapper;
import com.appmgr.api.model.Group;
import com.appmgr.api.model.Permission;
import com.appmgr.api.model.criteria.GroupCriteria;
import com.appmgr.api.repository.GroupRepository;
import com.appmgr.api.repository.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/v1/group")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class GroupController extends ABasicController {
    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private GroupMapper groupMapper;

    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('GR_C')")
    public ApiMessageDto<Void> create(@Valid @RequestBody CreateGroupForm createGroupForm, BindingResult bindingResult) {
        if (!isSuperAdmin()) {
            throw new UnauthorizationException("Not allowed create.");
        }
        Group group = groupRepository.findFirstByName(createGroupForm.getName());
        if (group != null) {
            throw new BadRequestException("Group is exist in this app", ErrorCode.GROUP_ERROR_NAME_EXIST);
        }
        group = groupMapper.fromCreateGroupFormToEntity(createGroupForm);
        List<Permission> permissions = new ArrayList<>();
        for (int i = 0; i < createGroupForm.getPermissions().length; i++) {
            Permission permission = permissionRepository.findById(createGroupForm.getPermissions()[i]).orElse(null);
            if (permission != null) {
                permissions.add(permission);
            }
        }
        group.setStatus(BaseConstant.STATUS_ACTIVE);
        group.setPermissions(permissions);
        groupRepository.save(group);
        return makeSuccessResponse("Create Group success");
    }

    @PutMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('GR_U')")
    public ApiMessageDto<Void> update(@Valid @RequestBody UpdateGroupForm updateGroupForm, BindingResult bindingResult) {
        if (!isSuperAdmin()) {
            throw new UnauthorizationException("Not allowed update.");
        }
        Group group = groupRepository.findById(updateGroupForm.getId())
                .orElseThrow(() -> new NotFoundException("Not found Group", ErrorCode.GROUP_ERROR_NOT_FOUND));
        Group otherGroup = groupRepository.findFirstByNameAndIdNot(updateGroupForm.getName(), updateGroupForm.getId());
        if (otherGroup != null && !Objects.equals(updateGroupForm.getId(), otherGroup.getId())) {
            throw new BadRequestException("Cant update this group name because it is exist", ErrorCode.GROUP_ERROR_NAME_EXIST);
        }
        group.setName(updateGroupForm.getName());
        group.setDescription(updateGroupForm.getDescription());
        List<Permission> permissions = new ArrayList<>();
        for (int i = 0; i < updateGroupForm.getPermissions().length; i++) {
            Permission permission = permissionRepository.findById(updateGroupForm.getPermissions()[i]).orElse(null);
            if (permission != null) {
                permissions.add(permission);
            }
        }
        group.setPermissions(permissions);
        groupRepository.save(group);
        return makeSuccessResponse("Update group success");
    }

    @GetMapping(value = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('GR_V')")
    public ApiMessageDto<GroupDto> get(@PathVariable("id") Long id) {
        if (!isSuperAdmin()) {
            throw new UnauthorizationException("Not allowed to get.");
        }
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Not found Group", ErrorCode.GROUP_ERROR_NOT_FOUND));
        return makeSuccessResponse(groupMapper.fromEntityToGroupDto(group), "Get group success");
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('GR_L')")
    public ApiMessageDto<ResponseListDto<List<GroupDto>>> list(GroupCriteria groupCriteria, Pageable pageable) {
        if (!isSuperAdmin()) {
            throw new UnauthorizationException("Not allowed list group.");
        }
        Page<Group> groups = groupRepository.findAll(groupCriteria.getSpecification(), PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(new Sort.Order(Sort.Direction.DESC, "createdDate"))));
        return makeSuccessResponse(makeResponseListDto(groups, groupMapper::fromEntityToGroupDtoList), "List Group success");
    }

    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('GR_D')")
    public ApiMessageDto<Void> delete(@PathVariable("id") Long id) {
        if (!isSuperAdmin()) {
            throw new UnauthorizationException("Not allowed list group.");
        }
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Can not found group", ErrorCode.GROUP_ERROR_NOT_FOUND));
        if (group.getIsSystemRole()) {
            throw new BadRequestException("Cannot delete system role", ErrorCode.GROUP_PERMISSION_ERROR_NAME_EXIST);
        }
        group.setPermissions(new ArrayList<>());
        groupRepository.save(group);
        return makeSuccessResponse("Delete Group success");
    }

    @GetMapping(value = "/auto-complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiMessageDto<ResponseListDto<List<GroupDto>>> autoComplete(GroupCriteria groupCriteria) {
        Pageable pageable = PageRequest.of(0, 10);
        groupCriteria.setStatus(BaseConstant.STATUS_ACTIVE);
        Page<Group> groups = groupRepository.findAll(groupCriteria.getSpecification(), pageable);
        return makeSuccessResponse(makeResponseListDto(groups, groupMapper::convertGroupToAutoCompleteDto), "Get auto complete Groups success");
    }
}
