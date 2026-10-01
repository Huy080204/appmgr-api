package com.appmgr.api.controller;

import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.ResponseListDto;
import com.appmgr.api.dto.permission.PermissionDto;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.exception.UnauthorizationException;
import com.appmgr.api.form.permission.CreatePermissionForm;
import com.appmgr.api.form.permission.UpdatePermissionForm;
import com.appmgr.api.mapper.PermissionMapper;
import com.appmgr.api.model.Permission;
import com.appmgr.api.model.criteria.PermissionCriteria;
import com.appmgr.api.repository.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/v1/permission")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class PermissionController extends ABasicController {
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private PermissionMapper permissionMapper;

    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('PER_C')")
    public ApiMessageDto<Void> create(@Valid @RequestBody CreatePermissionForm createPermissionForm, BindingResult bindingResult) {
        if (!isSuperAdmin()) {
            throw new UnauthorizationException("Not allowed create.");
        }
        Permission permission = permissionMapper.fromCreatePermissionFormToEntity(createPermissionForm);
        permission.setPCode(createPermissionForm.getPermissionCode());
        permissionRepository.save(permission);
        return makeSuccessResponse("Create permission success");
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('PER_L')")
    public ApiMessageDto<ResponseListDto<List<PermissionDto>>> list(PermissionCriteria criteria, Pageable pageable) {
        if (!isSuperAdmin()) {
            throw new UnauthorizationException("Not allowed list.");
        }
        Page<Permission> permissions = permissionRepository.findAll(criteria.getSpecification(), pageable);
        return makeSuccessResponse(makeResponseListDto(permissions, permissionMapper::fromEntityToPermissionDtoList), "Get permissions list success");
    }

    @GetMapping(value = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('PER_V')")
    public ApiMessageDto<PermissionDto> get(@PathVariable("id") Long id) {
        if (!isSuperAdmin()) {
            throw new UnauthorizationException("Not allowed to get.");
        }
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Can not found permission", ErrorCode.PERMISSION_ERROR_NOT_FOUND));
        return makeSuccessResponse(permissionMapper.fromEntityToPermissionDto(permission), "Get group success");
    }

    @PutMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('PER_U')")
    public ApiMessageDto<Void> create(@Valid @RequestBody UpdatePermissionForm updatePermissionForm, BindingResult bindingResult) {
        if (!isSuperAdmin()) {
            throw new UnauthorizationException("Not allowed update.");
        }
        Permission permission = permissionRepository.findById(updatePermissionForm.getId())
                .orElseThrow(() -> new NotFoundException("Can not found permission", ErrorCode.PERMISSION_ERROR_NOT_FOUND));
        permission.setName(updatePermissionForm.getName());
        permission.setDescription(updatePermissionForm.getDescription());
        permission.setAction(updatePermissionForm.getAction());
        permission.setShowMenu(updatePermissionForm.getShowMenu());
        permission.setPCode(updatePermissionForm.getPermissionCode());
        permission.setNameGroup(updatePermissionForm.getNameGroup());
        permissionRepository.save(permission);

        return makeSuccessResponse("Update permission success");
    }
}
