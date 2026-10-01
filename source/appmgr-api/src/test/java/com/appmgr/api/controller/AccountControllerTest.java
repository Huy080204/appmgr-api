package com.appmgr.api.controller;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.form.account.UpdateAccountAdminForm;
import com.appmgr.api.form.account.UpdateProfileAdminForm;
import com.appmgr.api.mapper.AccountMapper;
import com.appmgr.api.model.Account;
import com.appmgr.api.model.Group;
import com.appmgr.api.repository.AccountRepository;
import com.appmgr.api.repository.GroupRepository;
import com.appmgr.api.service.BaseApiService;
import com.appmgr.api.service.FileService;
import com.appmgr.api.service.impl.UserServiceImpl;
import com.appmgr.api.utils.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link AccountController}, scoped to the avatar-file-cleanup behavior added on
 * update-admin/update-profile-admin/delete (this project's first companion test for
 * AccountController — the rest of its endpoints are pre-existing and out of scope here).
 */
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private BaseApiService baseApiService;

    @Mock
    private FileService fileService;

    @Mock
    private UserServiceImpl userService;

    @InjectMocks
    private AccountController accountController;

    // --------------------------------------------------------- update-admin

    @Test
    void shouldDeleteOldAvatarWhenUpdateAdminAvatarPathChanges() {
        // Arrange
        lenient().when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt(1L));
        BindingResult bindingResult = mock(BindingResult.class);
        UpdateAccountAdminForm form = new UpdateAccountAdminForm();
        form.setId(1L);
        form.setGroupId(1L);
        form.setAvatarPath("/avatar/new.png");

        Account account = new Account();
        account.setId(1L);
        account.setAvatarPath("/avatar/old.png");
        Group group = new Group();
        group.setId(1L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        doAnswer(invocation -> {
            UpdateAccountAdminForm f = invocation.getArgument(0);
            Account a = invocation.getArgument(1);
            a.setAvatarPath(f.getAvatarPath());
            return null;
        }).when(accountMapper).mappingUpdateAdminFormToEntity(form, account);

        // Act
        ApiMessageDto<Void> result = accountController.updateAdmin(form, bindingResult);

        // Assert
        assertThat(result.getResult()).isTrue();
        verify(fileService).deleteFile("/avatar/old.png");
        verify(accountRepository).save(account);
        assertThat(account.getAvatarPath()).isEqualTo("/avatar/new.png");
    }

    @Test
    void shouldNotDeleteOldAvatarWhenUpdateAdminAvatarPathIsUnchanged() {
        // Arrange
        lenient().when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt(1L));
        BindingResult bindingResult = mock(BindingResult.class);
        UpdateAccountAdminForm form = new UpdateAccountAdminForm();
        form.setId(1L);
        form.setGroupId(1L);
        form.setAvatarPath("/avatar/same.png");

        Account account = new Account();
        account.setId(1L);
        account.setAvatarPath("/avatar/same.png");
        Group group = new Group();
        group.setId(1L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        // Act
        accountController.updateAdmin(form, bindingResult);

        // Assert
        verify(fileService, never()).deleteFile(any());
    }

    @Test
    void shouldDeleteOldAvatarWhenUpdateAdminClearsAvatarPathToNull() {
        // Arrange
        lenient().when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt(1L));
        BindingResult bindingResult = mock(BindingResult.class);
        UpdateAccountAdminForm form = new UpdateAccountAdminForm();
        form.setId(1L);
        form.setGroupId(1L);
        form.setAvatarPath(null);

        Account account = new Account();
        account.setId(1L);
        account.setAvatarPath("/avatar/old.png");
        Group group = new Group();
        group.setId(1L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        doAnswer(invocation -> {
            UpdateAccountAdminForm f = invocation.getArgument(0);
            Account a = invocation.getArgument(1);
            a.setAvatarPath(f.getAvatarPath());
            return null;
        }).when(accountMapper).mappingUpdateAdminFormToEntity(form, account);

        // Act
        accountController.updateAdmin(form, bindingResult);

        // Assert
        verify(fileService).deleteFile("/avatar/old.png");
        assertThat(account.getAvatarPath()).isNull();
    }

    // --------------------------------------------------- update-profile-admin

    @Test
    void shouldDeleteOldAvatarWhenUpdateProfileAdminAvatarPathChanges() {
        // Arrange
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt(1L));
        UpdateProfileAdminForm form = new UpdateProfileAdminForm();
        form.setFullName("Admin One");
        form.setAvatarPath("/avatar/new.png");
        form.setOldPassword("current-password");

        Account account = new Account();
        account.setId(1L);
        account.setAvatarPath("/avatar/old.png");
        account.setPassword("encoded-current-password");

        when(accountRepository.findByIdAndStatus(1L, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("current-password", "encoded-current-password")).thenReturn(true);
        doAnswer(invocation -> {
            UpdateProfileAdminForm f = invocation.getArgument(0);
            Account a = invocation.getArgument(1);
            a.setAvatarPath(f.getAvatarPath());
            return null;
        }).when(accountMapper).mappingUpdateProfileAdminFormToEntity(form, account);

        // Act
        ApiMessageDto<Void> result = accountController.updateProfileAdmin(form, mock(BindingResult.class));

        // Assert
        assertThat(result.getResult()).isTrue();
        verify(fileService).deleteFile("/avatar/old.png");
        assertThat(account.getAvatarPath()).isEqualTo("/avatar/new.png");
    }

    @Test
    void shouldNotDeleteOldAvatarWhenUpdateProfileAdminAvatarPathIsUnchanged() {
        // Arrange
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt(1L));
        UpdateProfileAdminForm form = new UpdateProfileAdminForm();
        form.setFullName("Admin One");
        form.setAvatarPath("/avatar/same.png");
        form.setOldPassword("current-password");

        Account account = new Account();
        account.setId(1L);
        account.setAvatarPath("/avatar/same.png");
        account.setPassword("encoded-current-password");

        when(accountRepository.findByIdAndStatus(1L, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("current-password", "encoded-current-password")).thenReturn(true);

        // Act
        accountController.updateProfileAdmin(form, mock(BindingResult.class));

        // Assert
        verify(fileService, never()).deleteFile(any());
    }

    @Test
    void shouldDeleteOldAvatarWhenUpdateProfileAdminClearsAvatarPathToNull() {
        // Arrange
        when(userService.getAddInfoFromToken()).thenReturn(TestUtils.superAdminJwt(1L));
        UpdateProfileAdminForm form = new UpdateProfileAdminForm();
        form.setFullName("Admin One");
        form.setAvatarPath(null);
        form.setOldPassword("current-password");

        Account account = new Account();
        account.setId(1L);
        account.setAvatarPath("/avatar/old.png");
        account.setPassword("encoded-current-password");

        when(accountRepository.findByIdAndStatus(1L, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("current-password", "encoded-current-password")).thenReturn(true);
        doAnswer(invocation -> {
            UpdateProfileAdminForm f = invocation.getArgument(0);
            Account a = invocation.getArgument(1);
            a.setAvatarPath(f.getAvatarPath());
            return null;
        }).when(accountMapper).mappingUpdateProfileAdminFormToEntity(form, account);

        // Act
        accountController.updateProfileAdmin(form, mock(BindingResult.class));

        // Assert
        verify(fileService).deleteFile("/avatar/old.png");
        assertThat(account.getAvatarPath()).isNull();
    }

    // ------------------------------------------------------------------ delete

    @Test
    void shouldDeleteAvatarFileWhenDeletingAccountWithNonBlankAvatarPath() {
        // Arrange
        Account account = new Account();
        account.setId(1L);
        account.setAvatarPath("/avatar/to-delete.png");
        account.setIsSuperAdmin(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        // Act
        ApiMessageDto<Void> result = accountController.delete(1L);

        // Assert
        assertThat(result.getResult()).isTrue();
        verify(fileService).deleteFile("/avatar/to-delete.png");
        verify(accountRepository).deleteById(1L);
    }

    @Test
    void shouldNotDeleteFileWhenDeletingAccountWithBlankAvatarPath() {
        // Arrange
        Account account = new Account();
        account.setId(1L);
        account.setIsSuperAdmin(false);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        // Act
        accountController.delete(1L);

        // Assert
        verify(fileService, never()).deleteFile(any());
        verify(accountRepository).deleteById(1L);
    }

    @Test
    void shouldThrowBadRequestWhenDeletingSuperAdminAccount() {
        // Arrange
        Account account = new Account();
        account.setId(1L);
        account.setIsSuperAdmin(true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        // Act + Assert
        assertThatThrownBy(() -> accountController.delete(1L))
                .isInstanceOf(BadRequestException.class);
        verify(fileService, never()).deleteFile(any());
        verify(accountRepository, never()).deleteById(any());
    }
}
