package com.appmgr.api.controller;

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
import com.appmgr.api.service.impl.UserServiceImpl;
import com.appmgr.api.utils.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.BindingResult;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

    @Mock
    private UserServiceImpl userService;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private GroupMapper groupMapper;
    @InjectMocks
    private GroupController controller;

    private CreateGroupForm createGroupForm() {
        CreateGroupForm form = new CreateGroupForm();
        form.setName("Admin");
        form.setDescription("Admin group");
        form.setPermissions(new Long[]{1L});
        return form;
    }

    private UpdateGroupForm updateGroupForm() {
        UpdateGroupForm form = new UpdateGroupForm();
        form.setId(1L);
        form.setName("Admin");
        form.setDescription("Admin group");
        form.setPermissions(new Long[]{1L});
        return form;
    }

    @Test
    void shouldCreateGroupWhenSuperAdminAndNameNotExist() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        when(groupRepository.findFirstByName("Admin")).thenReturn(null);
        Group group = new Group();
        when(groupMapper.fromCreateGroupFormToEntity(any(CreateGroupForm.class))).thenReturn(group);
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(new Permission()));

        ApiMessageDto<Void> result = controller.create(createGroupForm(), mock(BindingResult.class));

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Create Group success");
        verify(groupRepository).save(group);
        assertThat(group.getPermissions()).hasSize(1);
    }

    @Test
    void shouldThrowUnauthorizationWhenCreateNotSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.notSuperAdminJwt());

        assertThatThrownBy(() -> controller.create(createGroupForm(), mock(BindingResult.class)))
                .isInstanceOf(UnauthorizationException.class);
        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void shouldThrowBadRequestWhenCreateGroupNameExists() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        when(groupRepository.findFirstByName("Admin")).thenReturn(new Group());

        assertThatThrownBy(() -> controller.create(createGroupForm(), mock(BindingResult.class)))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GROUP_ERROR_NAME_EXIST);
        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void shouldUpdateGroupWhenSuperAdminAndValid() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        Group group = new Group();
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupRepository.findFirstByNameAndIdNot("Admin", 1L)).thenReturn(null);
        when(permissionRepository.findById(1L)).thenReturn(Optional.of(new Permission()));

        ApiMessageDto<Void> result = controller.update(updateGroupForm(), mock(BindingResult.class));

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Update group success");
        assertThat(group.getName()).isEqualTo("Admin");
        assertThat(group.getDescription()).isEqualTo("Admin group");
        verify(groupRepository).save(group);
    }

    @Test
    void shouldThrowUnauthorizationWhenUpdateNotSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.notSuperAdminJwt());

        assertThatThrownBy(() -> controller.update(updateGroupForm(), mock(BindingResult.class)))
                .isInstanceOf(UnauthorizationException.class);
        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void shouldThrowNotFoundWhenUpdateGroupIdDoesNotExist() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        when(groupRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(updateGroupForm(), mock(BindingResult.class)))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GROUP_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowBadRequestWhenUpdateGroupNameExists() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        when(groupRepository.findById(1L)).thenReturn(Optional.of(new Group()));
        Group otherGroup = new Group();
        otherGroup.setId(2L);
        when(groupRepository.findFirstByNameAndIdNot("Admin", 1L)).thenReturn(otherGroup);

        assertThatThrownBy(() -> controller.update(updateGroupForm(), mock(BindingResult.class)))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GROUP_ERROR_NAME_EXIST);
        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void shouldReturnGroupDtoWhenIdExists() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        Group group = new Group();
        GroupDto dto = new GroupDto();
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupMapper.fromEntityToGroupDto(group)).thenReturn(dto);

        ApiMessageDto<GroupDto> result = controller.get(1L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData()).isSameAs(dto);
        assertThat(result.getMessage()).isEqualTo("Get group success");
    }

    @Test
    void shouldThrowUnauthorizationWhenGetNotSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.notSuperAdminJwt());

        assertThatThrownBy(() -> controller.get(1L))
                .isInstanceOf(UnauthorizationException.class);
    }

    @Test
    void shouldThrowNotFoundWhenGetGroupIdDoesNotExist() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        when(groupRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(1L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GROUP_ERROR_NOT_FOUND);
    }

    @Test
    void shouldReturnGroupListWhenSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        Page<Group> page = new PageImpl<>(Collections.singletonList(new Group()));
        List<GroupDto> dtos = Collections.singletonList(new GroupDto());
        when(groupRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(groupMapper.fromEntityToGroupDtoList(any())).thenReturn(dtos);

        ApiMessageDto<ResponseListDto<List<GroupDto>>> result =
                controller.list(new GroupCriteria(), PageRequest.of(0, 10));

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getContent()).isSameAs(dtos);
        assertThat(result.getData().getTotalElements()).isEqualTo(1);
        assertThat(result.getData().getTotalPages()).isEqualTo(1);
        assertThat(result.getMessage()).isEqualTo("List Group success");
    }

    @Test
    void shouldThrowUnauthorizationWhenListNotSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.notSuperAdminJwt());

        assertThatThrownBy(() -> controller.list(new GroupCriteria(), PageRequest.of(0, 10)))
                .isInstanceOf(UnauthorizationException.class);
    }

    @Test
    void shouldDeleteGroupWhenIdExistsAndNotSystemRole() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        Group group = new Group();
        group.setIsSystemRole(false);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        ApiMessageDto<Void> result = controller.delete(1L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Delete Group success");
        assertThat(group.getPermissions()).isEmpty();
        verify(groupRepository).save(group);
    }

    @Test
    void shouldThrowUnauthorizationWhenDeleteNotSuperAdmin() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.notSuperAdminJwt());

        assertThatThrownBy(() -> controller.delete(1L))
                .isInstanceOf(UnauthorizationException.class);
        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void shouldThrowNotFoundWhenDeleteGroupIdDoesNotExist() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        when(groupRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.delete(1L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GROUP_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowBadRequestWhenDeleteSystemRoleGroup() {
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt());
        Group group = new Group();
        group.setIsSystemRole(true);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> controller.delete(1L))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GROUP_PERMISSION_ERROR_NAME_EXIST);
        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void shouldReturnAutoCompleteGroupListWhenCalled() {
        Page<Group> page = new PageImpl<>(Collections.singletonList(new Group()));
        List<GroupDto> dtos = Collections.singletonList(new GroupDto());
        when(groupRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
        when(groupMapper.convertGroupToAutoCompleteDto(any())).thenReturn(dtos);

        ApiMessageDto<ResponseListDto<List<GroupDto>>> result = controller.autoComplete(new GroupCriteria());

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getContent()).isSameAs(dtos);
        assertThat(result.getMessage()).isEqualTo("Get auto complete Groups success");
    }
}
