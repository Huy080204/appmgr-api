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
import com.appmgr.api.service.impl.UserServiceImpl;
import com.appmgr.api.utils.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionControllerTest {

    @Mock private PermissionRepository permissionRepository;
    @Mock private PermissionMapper permissionMapper;
    @Mock private UserServiceImpl userService;
    @InjectMocks private PermissionController controller;

    @Test
    void shouldThrowUnauthorizationWhenCreateNotSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.notSuperAdminJwt());
        CreatePermissionForm form = new CreatePermissionForm();

        assertThatThrownBy(() -> controller.create(form, null))
                .isInstanceOf(UnauthorizationException.class);
    }

    @Test
    void shouldCreatePermissionSuccessWhenSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        CreatePermissionForm form = new CreatePermissionForm();
        form.setPermissionCode("PER_C");
        Permission entity = new Permission();
        when(permissionMapper.fromCreatePermissionFormToEntity(form)).thenReturn(entity);

        ApiMessageDto<Void> result = controller.create(form, null);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Create permission success");
        assertThat(entity.getPCode()).isEqualTo("PER_C");
        verify(permissionRepository).save(entity);
    }

    @Test
    void shouldThrowUnauthorizationWhenListNotSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.notSuperAdminJwt());
        PermissionCriteria criteria = new PermissionCriteria();
        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> controller.list(criteria, pageable))
                .isInstanceOf(UnauthorizationException.class);
    }

    @Test
    void shouldReturnPermissionListWhenSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        PermissionCriteria criteria = new PermissionCriteria();
        Pageable pageable = PageRequest.of(0, 10);
        Permission entity = new Permission();
        Page<Permission> page = new PageImpl<>(Collections.singletonList(entity));
        List<PermissionDto> dtoList = Collections.singletonList(new PermissionDto());
        when(permissionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(permissionMapper.fromEntityToPermissionDtoList(page.getContent())).thenReturn(dtoList);

        ApiMessageDto<ResponseListDto<List<PermissionDto>>> result = controller.list(criteria, pageable);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getContent()).isSameAs(dtoList);
        assertThat(result.getData().getTotalElements()).isEqualTo(1);
        assertThat(result.getData().getTotalPages()).isEqualTo(1);
        assertThat(result.getMessage()).isEqualTo("Get permissions list success");
    }

    @Test
    void shouldThrowUnauthorizationWhenGetNotSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.notSuperAdminJwt());

        assertThatThrownBy(() -> controller.get(1L))
                .isInstanceOf(UnauthorizationException.class);
    }

    @Test
    void shouldThrowNotFoundWhenGetPermissionIdDoesNotExist() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        when(permissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(1L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PERMISSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldReturnPermissionDtoWhenIdExists() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        Permission entity = new Permission();
        PermissionDto dto = new PermissionDto();
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(permissionMapper.fromEntityToPermissionDto(entity)).thenReturn(dto);

        ApiMessageDto<PermissionDto> result = controller.get(1L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData()).isSameAs(dto);
        assertThat(result.getMessage()).isEqualTo("Get group success");
        verify(permissionRepository).findById(1L);
    }

    @Test
    void shouldThrowUnauthorizationWhenUpdateNotSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.notSuperAdminJwt());
        UpdatePermissionForm form = new UpdatePermissionForm();

        assertThatThrownBy(() -> controller.create(form, null))
                .isInstanceOf(UnauthorizationException.class);
    }

    @Test
    void shouldThrowNotFoundWhenUpdatePermissionIdDoesNotExist() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        UpdatePermissionForm form = new UpdatePermissionForm();
        form.setId(1L);
        when(permissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(form, null))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.PERMISSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldUpdatePermissionSuccessWhenSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        UpdatePermissionForm form = new UpdatePermissionForm();
        form.setId(1L);
        form.setName("Create Course");
        form.setDescription("desc");
        form.setAction("CREATE");
        form.setShowMenu(true);
        form.setPermissionCode("COURSE_C");
        form.setNameGroup("Course");
        Permission entity = new Permission();
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(entity));

        ApiMessageDto<Void> result = controller.create(form, null);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Update permission success");
        assertThat(entity.getName()).isEqualTo("Create Course");
        assertThat(entity.getDescription()).isEqualTo("desc");
        assertThat(entity.getAction()).isEqualTo("CREATE");
        assertThat(entity.getShowMenu()).isTrue();
        assertThat(entity.getPCode()).isEqualTo("COURSE_C");
        assertThat(entity.getNameGroup()).isEqualTo("Course");
        verify(permissionRepository).save(entity);
    }
}
