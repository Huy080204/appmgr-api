package com.appmgr.api.service.impl;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.jwt.BaseJwt;
import com.appmgr.api.model.Account;
import com.appmgr.api.model.Group;
import com.appmgr.api.repository.AccountRepository;
import com.appmgr.api.repository.GroupRepository;
import com.appmgr.api.service.mfa.TotpManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Transactional
@Service(value = "userService")
@Slf4j
public class UserServiceImpl implements UserDetailsService {
    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpManager totpManager;

    @Value("${mfa}")
    private Boolean isMfaEnable;

    @Override
    public UserDetails loadUserByUsername(String userId) {
        Account user = accountRepository.findAccountByUsername(userId);
        if (user == null) {
            log.error("Invalid username or password.");
            throw new UsernameNotFoundException("Invalid username or password.");
        }
        boolean enabled = true;
        if (user.getStatus() != BaseConstant.STATUS_ACTIVE) {
            log.error("User had been locked");
            enabled = false;
        }
        Set<GrantedAuthority> grantedAuthorities = getAccountPermission(user);

        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(), enabled, true, true, true, grantedAuthorities);
    }

    public UserDetails loadUserForPasswordOtp(String username, String password, String otp) {
        if (isMfaEnable && StringUtils.isBlank(otp)) {
            throw new BadRequestException("otp cannot be null", ErrorCode.AUTH_GRANT_TYPE_PASSWORD_MFA_ERROR_OTP_BLANK);
        }

        Account user = accountRepository.findFirstByUsernameOrEmailOrPhoneAndStatus(username, BaseConstant.STATUS_ACTIVE)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password!"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new UsernameNotFoundException("Invalid username or password!");
        }

        // if system enable mfa and user not config mfa --> verify otp to config mfa for user
        if (isMfaEnable) {
            boolean isVerified = user.getSecretKey() != null && totpManager.verifyCode(otp, user.getSecretKey());
            if (!isVerified) {
                throw new BadRequestException("Verified Fail", ErrorCode.ACCOUNT_ERROR_OTP_INVALID);
            }
            if (!Boolean.TRUE.equals(user.getIsMfa())) {
                user.setIsMfa(true);
                accountRepository.save(user);
            }
        }
        Set<GrantedAuthority> grantedAuthorities = getAccountPermission(user);
        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(), true, true, true, true, grantedAuthorities);
    }

    private Set<GrantedAuthority> getAccountPermission(Account user) {
        List<String> roles = new ArrayList<>();
        user.getGroup().getPermissions().stream().filter(f -> f.getPCode() != null).forEach(pName -> roles.add(pName.getPCode()));
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())).collect(Collectors.toSet());
    }

    private Set<GrantedAuthority> getAccountPermissionCustomEmployee(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(permissions.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toSet());
    }

    private Set<GrantedAuthority> getAccountPermissionCustomUser(String kind) {
        int groupKind;
        try {
            groupKind = Integer.parseInt(kind);
        } catch (NumberFormatException e) {
            return Collections.emptySet();
        }

        Group group = groupRepository.findFirstByKind(groupKind);
        if (group == null) {
            return Collections.emptySet();
        }

        List<String> roles = new ArrayList<>();
        group.getPermissions().stream().filter(f -> f.getPCode() != null).forEach(pName -> roles.add(pName.getPCode()));
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())).collect(Collectors.toSet());
    }

    public String getAttributeFromToken(String attribute) {
        Map<String, Object> map = getAttributeFromToken();
        if (map != null) {
            return String.valueOf(map.get(attribute));
        }
        return null;
    }

    public Map<String, Object> getAttributeFromToken() {
        Jwt jwt = getCurrentJwt();
        if (jwt != null) {
            return jwt.getClaims();
        }
        return null;
    }

    public BaseJwt getAddInfoFromToken() {
        Jwt jwt = getCurrentJwt();
        if (jwt != null) {
            String encodedData = jwt.getClaimAsString("additional_info");
            //idStr -> json
            if (encodedData != null && !encodedData.isEmpty()) {
                return BaseJwt.decode(encodedData);
            }
            return null;
        }
        return null;
    }

    public String getCurrentToken() {
        Jwt jwt = getCurrentJwt();
        if (jwt != null) {
            return jwt.getTokenValue();
        }
        return null;
    }

    private Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) authentication).getToken();
        }
        return null;
    }
}
