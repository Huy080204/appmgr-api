package com.appmgr.api.service;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.jwt.BaseJwt;
import com.appmgr.api.model.Account;
import com.appmgr.api.model.Group;
import com.appmgr.api.model.Permission;
import com.appmgr.api.repository.AccountRepository;
import com.appmgr.api.service.impl.UserServiceImpl;
import com.appmgr.api.service.mfa.TotpManager;
import com.appmgr.api.utils.ZipUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TotpManager totpManager;

    @InjectMocks
    private UserServiceImpl userService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnTokenValueWhenAuthenticationIsJwt() {
        authenticateWith(jwt(Map.of("user_name", "admin")));

        String token = userService.getCurrentToken();

        assertThat(token).isEqualTo("token-value");
    }

    @Test
    void shouldReturnNullTokenWhenCallerIsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(userService.getCurrentToken()).isNull();
    }

    @Test
    void shouldReturnNullTokenWhenNobodyIsAuthenticated() {
        assertThat(userService.getCurrentToken()).isNull();
    }

    @Test
    void shouldDecodeAdditionalInfoIncludingTenantIdWhenJwtCarriesIt() {
        String additionalInfo = ZipUtils.zipString("42|-1|1|<>|-1|1|admin|-1|-1|true|tenant-9");
        authenticateWith(jwt(Map.of("additional_info", additionalInfo)));

        BaseJwt result = userService.getAddInfoFromToken();

        assertThat(result.getAccountId()).isEqualTo(42L);
        assertThat(result.getUsername()).isEqualTo("admin");
        assertThat(result.getIsSuperAdmin()).isTrue();
        assertThat(result.getTenantId()).isEqualTo("tenant-9");
    }

    @Test
    void shouldReturnNullAdditionalInfoWhenClaimIsMissing() {
        authenticateWith(jwt(Map.of("user_name", "admin")));

        assertThat(userService.getAddInfoFromToken()).isNull();
    }

    @Test
    void shouldReturnNullAdditionalInfoWhenClaimIsEmpty() {
        authenticateWith(jwt(Map.of("additional_info", "")));

        assertThat(userService.getAddInfoFromToken()).isNull();
    }

    @Test
    void shouldReturnNullAdditionalInfoWhenCallerIsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(userService.getAddInfoFromToken()).isNull();
    }

    @Test
    void shouldReturnClaimsWhenReadingAttributesFromToken() {
        authenticateWith(jwt(Map.of("user_name", "admin")));

        assertThat(userService.getAttributeFromToken()).containsEntry("user_name", "admin");
        assertThat(userService.getAttributeFromToken("user_name")).isEqualTo("admin");
    }

    @Test
    void shouldReturnNullAttributesWhenNobodyIsAuthenticated() {
        assertThat(userService.getAttributeFromToken()).isNull();
        assertThat(userService.getAttributeFromToken("user_name")).isNull();
    }

    @Test
    void shouldLoadEnabledUserWithUpperCasedRoleAuthorities() {
        when(accountRepository.findAccountByUsername("admin")).thenReturn(account("admin", BaseConstant.STATUS_ACTIVE));

        UserDetails user = userService.loadUserByUsername("admin");

        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ACC_C");
    }

    @Test
    void shouldLoadDisabledUserWhenAccountIsLocked() {
        when(accountRepository.findAccountByUsername("admin")).thenReturn(account("admin", 0));

        UserDetails user = userService.loadUserByUsername("admin");

        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void shouldThrowUsernameNotFoundWhenAccountDoesNotExist() {
        when(accountRepository.findAccountByUsername("ghost")).thenReturn(null);

        assertThatThrownBy(() -> userService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void shouldThrowOtpBlankWhenMfaIsEnabledAndOtpIsBlank() {
        ReflectionTestUtils.setField(userService, "isMfaEnable", true);

        assertThatThrownBy(() -> userService.loadUserForPasswordOtp("admin", "secret", " "))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.AUTH_GRANT_TYPE_PASSWORD_MFA_ERROR_OTP_BLANK);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void shouldThrowUsernameNotFoundWhenOtpLoginUserDoesNotExist() {
        ReflectionTestUtils.setField(userService, "isMfaEnable", false);
        when(accountRepository.findFirstByUsernameOrEmailOrPhoneAndStatus("ghost", BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserForPasswordOtp("ghost", "secret", null))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void shouldThrowUsernameNotFoundWhenOtpLoginPasswordIsWrong() {
        ReflectionTestUtils.setField(userService, "isMfaEnable", false);
        Account account = account("admin", BaseConstant.STATUS_ACTIVE);
        when(accountRepository.findFirstByUsernameOrEmailOrPhoneAndStatus("admin", BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> userService.loadUserForPasswordOtp("admin", "wrong", null))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void shouldLoadUserWithoutCheckingOtpWhenMfaIsDisabled() {
        ReflectionTestUtils.setField(userService, "isMfaEnable", false);
        Account account = account("admin", BaseConstant.STATUS_ACTIVE);
        when(accountRepository.findFirstByUsernameOrEmailOrPhoneAndStatus("admin", BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);

        UserDetails user = userService.loadUserForPasswordOtp("admin", "secret", null);

        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ACC_C");
        verifyNoInteractions(totpManager);
    }

    @Test
    void shouldThrowOtpInvalidWhenCodeDoesNotVerify() {
        ReflectionTestUtils.setField(userService, "isMfaEnable", true);
        Account account = account("admin", BaseConstant.STATUS_ACTIVE);
        account.setSecretKey("totp-secret");
        when(accountRepository.findFirstByUsernameOrEmailOrPhoneAndStatus("admin", BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(totpManager.verifyCode("000000", "totp-secret")).thenReturn(false);

        assertThatThrownBy(() -> userService.loadUserForPasswordOtp("admin", "secret", "000000"))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ACCOUNT_ERROR_OTP_INVALID);
    }

    @Test
    void shouldThrowOtpInvalidWhenAccountHasNoSecretKey() {
        ReflectionTestUtils.setField(userService, "isMfaEnable", true);
        Account account = account("admin", BaseConstant.STATUS_ACTIVE);
        when(accountRepository.findFirstByUsernameOrEmailOrPhoneAndStatus("admin", BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);

        assertThatThrownBy(() -> userService.loadUserForPasswordOtp("admin", "secret", "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ACCOUNT_ERROR_OTP_INVALID);
        verifyNoInteractions(totpManager);
    }

    @Test
    void shouldEnableMfaOnAccountWhenCodeVerifiesForTheFirstTime() {
        ReflectionTestUtils.setField(userService, "isMfaEnable", true);
        Account account = account("admin", BaseConstant.STATUS_ACTIVE);
        account.setSecretKey("totp-secret");
        account.setIsMfa(false);
        when(accountRepository.findFirstByUsernameOrEmailOrPhoneAndStatus("admin", BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(totpManager.verifyCode("123456", "totp-secret")).thenReturn(true);

        userService.loadUserForPasswordOtp("admin", "secret", "123456");

        assertThat(account.getIsMfa()).isTrue();
        verify(accountRepository).save(account);
    }

    @Test
    void shouldNotSaveAccountWhenMfaIsAlreadyEnabled() {
        ReflectionTestUtils.setField(userService, "isMfaEnable", true);
        Account account = account("admin", BaseConstant.STATUS_ACTIVE);
        account.setSecretKey("totp-secret");
        account.setIsMfa(true);
        when(accountRepository.findFirstByUsernameOrEmailOrPhoneAndStatus("admin", BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(totpManager.verifyCode("123456", "totp-secret")).thenReturn(true);

        userService.loadUserForPasswordOtp("admin", "secret", "123456");

        verify(accountRepository, never()).save(account);
    }

    private static void authenticateWith(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token-value").header("alg", "HS256").claims(c -> c.putAll(claims)).build();
    }

    private static Account account(String username, int status) {
        Permission permission = new Permission();
        permission.setPCode("acc_c");
        Group group = new Group();
        group.setPermissions(List.of(permission));
        Account account = new Account();
        account.setUsername(username);
        account.setPassword("encoded");
        account.setStatus(status);
        account.setGroup(group);
        return account;
    }
}
