package com.appmgr.api.controller;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ApiResponse;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.ResponseListDto;
import com.appmgr.api.dto.account.AccountDto;
import com.appmgr.api.dto.account.AccountMfaDto;
import com.appmgr.api.dto.account.ForgetPasswordDto;
import com.appmgr.api.dto.account.RequestForgetPasswordForm;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.exception.UnauthorizationException;
import com.appmgr.api.form.account.CreateAccountAdminForm;
import com.appmgr.api.form.account.ForgetPasswordForm;
import com.appmgr.api.form.account.LoginForm;
import com.appmgr.api.form.account.UpdateAccountAdminForm;
import com.appmgr.api.form.account.UpdateMfaForm;
import com.appmgr.api.form.account.UpdateProfileAdminForm;
import com.appmgr.api.mapper.AccountMapper;
import com.appmgr.api.model.Account;
import com.appmgr.api.model.Group;
import com.appmgr.api.model.criteria.AccountCriteria;
import com.appmgr.api.repository.AccountRepository;
import com.appmgr.api.repository.GroupRepository;
import com.appmgr.api.service.BaseApiService;
import com.appmgr.api.service.FileService;
import com.appmgr.api.service.mfa.TotpManager;
import com.appmgr.api.utils.AESUtils;
import com.appmgr.api.utils.ConvertUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/v1/account")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class AccountController extends ABasicController {
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private BaseApiService baseApiService;
    @Autowired
    private FileService fileService;
    @Autowired
    private TotpManager totpManager;
    @Value("${mfa}")
    private Boolean isMfaEnable;

    @PostMapping(value = "/create-admin", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ACC_C_AD')")
    public ApiMessageDto<Void> createAdmin(@Valid @RequestBody CreateAccountAdminForm form, BindingResult bindingResult) {
        Account account = accountRepository.findAccountByUsername(form.getUsername());
        if (account != null) {
            throw new BadRequestException("[Account] Username exist", ErrorCode.ACCOUNT_ERROR_USERNAME_EXIST);
        }

        if (StringUtils.isNoneBlank(form.getEmail())
                && accountRepository.existsByEmailAndStatusNot(form.getEmail(), BaseConstant.STATUS_DELETE)) {
            throw new BadRequestException("[Account] Email existed", ErrorCode.ACCOUNT_ERROR_EMAIL_EXISTED);
        }

        if (StringUtils.isNoneBlank(form.getPhone())
                && accountRepository.existsByPhoneAndStatusNot(form.getPhone(), BaseConstant.STATUS_DELETE)) {
            throw new BadRequestException("[Account] Phone existed", ErrorCode.ACCOUNT_ERROR_PHONE_EXISTED);
        }

        Group group = groupRepository.findById(form.getGroupId())
                .orElseThrow(() -> new BadRequestException("[Group] Group not found", ErrorCode.GROUP_ERROR_NOT_FOUND));

        account = accountMapper.fromCreateAdminFormToEntity(form);
        account.setPassword(passwordEncoder.encode(form.getPassword()));
        account.setKind(BaseConstant.USER_KIND_ADMIN);
        account.setGroup(group);
        accountRepository.save(account);

        return makeSuccessResponse("Create account admin success");
    }

    @PutMapping(value = "/update-admin", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ACC_U_AD')")
    public ApiMessageDto<Void> updateAdmin(@Valid @RequestBody UpdateAccountAdminForm form, BindingResult bindingResult) {
        Account account = accountRepository.findById(form.getId())
                .orElseThrow(() -> new NotFoundException("[Account] Account not found", ErrorCode.ACCOUNT_ERROR_NOT_FOUND));

        Group group = groupRepository.findById(form.getGroupId())
                .orElseThrow(() -> new NotFoundException("[Group] Group not found", ErrorCode.GROUP_ERROR_NOT_FOUND));

        if (StringUtils.isNoneBlank(form.getEmail())
                && !form.getEmail().equals(account.getEmail())
                && accountRepository.existsByEmailAndStatusNot(form.getEmail(), BaseConstant.STATUS_DELETE)) {
            throw new BadRequestException("[Account] Email existed", ErrorCode.ACCOUNT_ERROR_EMAIL_EXISTED);
        }

        if (StringUtils.isNoneBlank(form.getPhone())
                && !form.getPhone().equals(account.getPhone())
                && accountRepository.existsByPhoneAndStatusNot(form.getPhone(), BaseConstant.STATUS_DELETE)) {
            throw new BadRequestException("[Account] Phone existed", ErrorCode.ACCOUNT_ERROR_PHONE_EXISTED);
        }

        if (StringUtils.isNoneBlank(form.getPassword())) {
            account.setPassword(passwordEncoder.encode(form.getPassword()));
        }

        String oldAvatarPath = account.getAvatarPath();
        if (oldAvatarPath != null && !Objects.equals(form.getAvatarPath(), oldAvatarPath)) {
            fileService.deleteFile(oldAvatarPath);
        }

        account.setGroup(group);
        accountMapper.mappingUpdateAdminFormToEntity(form, account);
        accountRepository.save(account);

        return makeSuccessResponse("Update account admin success");
    }

    @GetMapping(value = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ACC_V')")
    public ApiMessageDto<AccountDto> get(@PathVariable Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("[Account] Account not found", ErrorCode.ACCOUNT_ERROR_NOT_FOUND));
        return makeSuccessResponse(accountMapper.fromAccountToDtoShort(account), "Get account success");
    }

    @Transactional
    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ACC_D')")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("[Account] Account not found!", ErrorCode.ACCOUNT_ERROR_NOT_FOUND));
        if (account.getIsSuperAdmin()) {
            throw new BadRequestException("[Account] Account super admin cannot delete", ErrorCode.ACCOUNT_ERROR_NOT_DELETE_SUPPER_ADMIN);
        }
        // delete avatar file
        String avatarPath = account.getAvatarPath();
        if (StringUtils.isNoneBlank(avatarPath)) {
            fileService.deleteFile(avatarPath);
        }
        accountRepository.deleteById(id);

        return makeSuccessResponse("Delete Account success");
    }

    @GetMapping(value = "/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AccountDto> profile() {
        long id = getCurrentUser();
        Account account = accountRepository.findByIdAndStatus(id, BaseConstant.STATUS_ACTIVE)
                .orElseThrow(() -> new NotFoundException("[Account] Account not found", ErrorCode.ACCOUNT_ERROR_NOT_FOUND));

        ApiResponse<AccountDto> apiMessageDto = new ApiResponse<>();
        apiMessageDto.setData(accountMapper.fromAccountToDtoShort(account));
        apiMessageDto.setMessage("Get Account success");
        return apiMessageDto;
    }

    @PutMapping(value = "/update-profile-admin", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiMessageDto<Void> updateProfileAdmin(@Valid @RequestBody UpdateProfileAdminForm updateProfileAdminForm, BindingResult bindingResult) {
        Account account = accountRepository.findByIdAndStatus(getCurrentUser(), BaseConstant.STATUS_ACTIVE)
                .orElseThrow(() -> new NotFoundException("[Account] Account not found", ErrorCode.ACCOUNT_ERROR_NOT_FOUND));

        if (!passwordEncoder.matches(updateProfileAdminForm.getOldPassword(), account.getPassword())) {
            throw new BadRequestException("[Account] Wrong password", ErrorCode.ACCOUNT_ERROR_WRONG_PASSWORD);
        }

        if (StringUtils.isNoneBlank(updateProfileAdminForm.getPassword())) {
            account.setPassword(passwordEncoder.encode(updateProfileAdminForm.getPassword()));
        }

        if (StringUtils.isNoneBlank(updateProfileAdminForm.getPhone())
                && !updateProfileAdminForm.getPhone().equals(account.getPhone())
                && accountRepository.existsByPhoneAndStatusNot(updateProfileAdminForm.getPhone(), BaseConstant.STATUS_DELETE)) {
            throw new BadRequestException("[Account] Phone existed", ErrorCode.ACCOUNT_ERROR_PHONE_EXISTED);
        }

        String oldAvatarPath = account.getAvatarPath();
        if (oldAvatarPath != null && !Objects.equals(updateProfileAdminForm.getAvatarPath(), oldAvatarPath)) {
            fileService.deleteFile(oldAvatarPath);
        }
        accountMapper.mappingUpdateProfileAdminFormToEntity(updateProfileAdminForm, account);
        accountRepository.save(account);

        return makeSuccessResponse("Update admin account success");
    }

    @PostMapping(value = "/request-forget-password", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ForgetPasswordDto> requestForgetPassword(@Valid @RequestBody RequestForgetPasswordForm forgetForm, BindingResult bindingResult) {
        ApiResponse<ForgetPasswordDto> apiMessageDto = new ApiResponse<>();
        Account account = accountRepository.findAccountByEmail(forgetForm.getEmail())
                .orElseThrow(() -> new NotFoundException("[Account] Account not found", ErrorCode.ACCOUNT_ERROR_NOT_FOUND));

        String otp = baseApiService.getOTPForgetPassword();
        accountRepository.save(account);

        //send email
        baseApiService.sendEmail(account.getEmail(), "OTP: " + otp, "Reset password", false);

        ForgetPasswordDto forgetPasswordDto = new ForgetPasswordDto();
        String hash = AESUtils.encrypt(account.getId() + ";" + otp, true);
        forgetPasswordDto.setIdHash(hash);

        apiMessageDto.setResult(true);
        apiMessageDto.setData(forgetPasswordDto);
        apiMessageDto.setMessage("Request forget password successfully, please check email.");
        return apiMessageDto;
    }

    @PostMapping(value = "/forget-password", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Long> forgetPassword(@Valid @RequestBody ForgetPasswordForm forgetForm, BindingResult bindingResult) {
        ApiResponse<Long> apiMessageDto = new ApiResponse<>();

        String[] hash = AESUtils.decrypt(forgetForm.getIdHash(), true).split(";", 2);
        Long id = ConvertUtils.convertStringToLong(hash[0]);
        if (id <= 0) {
            throw new BadRequestException("[Account] Wrong hash reset password", ErrorCode.ACCOUNT_ERROR_WRONG_HASH_RESET_PASS);
        }

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("[Account] Account not found", ErrorCode.ACCOUNT_ERROR_NOT_FOUND));

        account.setPassword(passwordEncoder.encode(forgetForm.getNewPassword()));
        accountRepository.save(account);

        apiMessageDto.setResult(true);
        apiMessageDto.setMessage("Change password success.");
        return apiMessageDto;
    }

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ApiResponse<AccountMfaDto> login(@Valid @RequestBody LoginForm form, BindingResult bindingResult) {
        Account account = accountRepository.findFirstByUsernameOrEmailOrPhoneAndStatus(form.getUsername(), BaseConstant.STATUS_ACTIVE)
                .orElseThrow(() -> new NotFoundException("[Account] Account not found", ErrorCode.ACCOUNT_ERROR_NOT_FOUND));
        if (!passwordEncoder.matches(form.getPassword(), account.getPassword())) {
            throw new BadRequestException("[Account] Wrong password", ErrorCode.ACCOUNT_ERROR_WRONG_PASSWORD);
        }

        AccountMfaDto accountMfaDto = new AccountMfaDto();
        accountMfaDto.setIsMfaEnable(Boolean.TRUE.equals(isMfaEnable));
        if (Boolean.TRUE.equals(isMfaEnable)) {
            boolean isMfa = Boolean.TRUE.equals(account.getIsMfa());
            accountMfaDto.setIsMfa(isMfa);
            if (!isMfa) {
                if (StringUtils.isBlank(account.getSecretKey())) {
                    account.setSecretKey(totpManager.generateSecret());
                    accountRepository.save(account);
                }
                String qrCodeUrl;
                if (account.getUsername() != null) {
                    qrCodeUrl = totpManager.getUriForImage(account.getSecretKey(), account.getUsername());
                } else if (account.getPhone() != null) {
                    qrCodeUrl = totpManager.getUriForImage(account.getSecretKey(), account.getPhone());
                } else if (account.getEmail() != null) {
                    qrCodeUrl = totpManager.getUriForImage(account.getSecretKey(), account.getEmail());
                } else {
                    throw new BadRequestException("Account can not scanned qr to authorize");
                }
                accountMfaDto.setQrUrl(qrCodeUrl);
            }
        }

        ApiResponse<AccountMfaDto> apiMessageDto = new ApiResponse<>();
        apiMessageDto.setResult(true);
        apiMessageDto.setData(accountMfaDto);
        apiMessageDto.setMessage("Login success");
        return apiMessageDto;
    }

    @PutMapping(value = "/reset-mfa", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ACC_U_MFA')")
    @Transactional
    public ApiMessageDto<Void> resetMfa(@Valid @RequestBody UpdateMfaForm form, BindingResult bindingResult) {
        if (!isSuperAdmin()) {
            throw new UnauthorizationException("[Account] Not allowed to reset mfa.");
        }
        Account account = accountRepository.findById(form.getId())
                .orElseThrow(() -> new NotFoundException("[Account] Account not found", ErrorCode.ACCOUNT_ERROR_NOT_FOUND));
        account.setSecretKey(null);
        account.setIsMfa(false);
        accountRepository.save(account);
        return makeSuccessResponse("Reset mfa success");
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ACC_L')")
    public ApiMessageDto<ResponseListDto<List<AccountDto>>> listAccount(AccountCriteria accountCriteria, Pageable pageable) {
        Page<Account> accounts = accountRepository.findAll(accountCriteria.getSpecification(), pageable);
        return makeSuccessResponse(makeResponseListDto(accounts, accountMapper::fromEntityToAccountDtoList), "List account success");
    }

    @GetMapping(value = "/auto-complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiMessageDto<ResponseListDto<List<AccountDto>>> autoComplete(AccountCriteria criteria, Pageable pageable) {
        Page<Account> accounts = accountRepository.findAll(criteria.getSpecification(), pageable);
        return makeSuccessResponse(makeResponseListDto(accounts, accountMapper::fromAccountToAutoCompleteDtoList), "List account success");
    }
}
