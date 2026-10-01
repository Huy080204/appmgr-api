package com.appmgr.api.mapper;

import com.appmgr.api.dto.account.AccountDto;
import com.appmgr.api.form.account.CreateAccountAdminForm;
import com.appmgr.api.form.account.UpdateAccountAdminForm;
import com.appmgr.api.form.account.UpdateProfileAdminForm;
import com.appmgr.api.model.Account;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        uses = {GroupMapper.class})
public interface AccountMapper {
    @Mapping(source = "id", target = "id")
    @Mapping(source = "kind", target = "kind")
    @Mapping(source = "username", target = "username")
    @Mapping(source = "phone", target = "phone")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "fullName", target = "fullName")
    @Mapping(source = "group", target = "group", qualifiedByName = "fromEntityToGroupDto")
    @Mapping(source = "avatarPath", target = "avatarPath")
    @Mapping(source = "isSuperAdmin", target = "isSuperAdmin")
    @Mapping(source = "isMfa", target = "isMfa")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromAccountToDto")
    AccountDto fromAccountToDto(Account account);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "avatarPath", target = "avatarPath")
    @Mapping(source = "fullName", target = "fullName")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromAccountToAutoCompleteDto")
    AccountDto fromAccountToAutoCompleteDto(Account account);

    @IterableMapping(elementTargetType = AccountDto.class, qualifiedByName = "fromAccountToAutoCompleteDto")
    List<AccountDto> fromAccountToAutoCompleteDtoList(List<Account> list);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "kind", target = "kind")
    @Mapping(source = "username", target = "username")
    @Mapping(source = "phone", target = "phone")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "fullName", target = "fullName")
    @Mapping(source = "avatarPath", target = "avatarPath")
    @Mapping(source = "isSuperAdmin", target = "isSuperAdmin")
    @Mapping(source = "isMfa", target = "isMfa")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "group", target = "group", qualifiedByName = "fromEntityToGroupDtoShort")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromAccountToDtoShort")
    AccountDto fromAccountToDtoShort(Account account);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "kind", target = "kind")
    @Mapping(source = "username", target = "username")
    @Mapping(source = "phone", target = "phone")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "fullName", target = "fullName")
    @Mapping(source = "group", target = "group", qualifiedByName = "fromGroupToAutoCompleteDto")
    @Mapping(source = "avatarPath", target = "avatarPath")
    @Mapping(source = "isSuperAdmin", target = "isSuperAdmin")
    @Mapping(source = "isMfa", target = "isMfa")
    @Mapping(source = "status", target = "status")
    @BeanMapping(ignoreByDefault = true)
    @Named("fromAccountToDtoForServer")
    AccountDto fromAccountToDtoForServer(Account account);

    @IterableMapping(elementTargetType = AccountDto.class, qualifiedByName = "fromAccountToDtoForServer")
    @Named("fromEntityToAccountDtoList")
    List<AccountDto> fromEntityToAccountDtoList(List<Account> accounts);

    @Mapping(source = "username", target = "username")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "phone", target = "phone")
    @Mapping(source = "fullName", target = "fullName")
    @Mapping(source = "avatarPath", target = "avatarPath", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(source = "status", target = "status")
    @BeanMapping(ignoreByDefault = true)
    Account fromCreateAdminFormToEntity(CreateAccountAdminForm form);

    @Mapping(source = "username", target = "username")
    @Mapping(source = "phone", target = "phone")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "fullName", target = "fullName")
    @Mapping(source = "avatarPath", target = "avatarPath", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(source = "status", target = "status")
    @BeanMapping(ignoreByDefault = true)
    void mappingUpdateAdminFormToEntity(UpdateAccountAdminForm form, @MappingTarget Account account);

    @Mapping(source = "phone", target = "phone")
    @Mapping(source = "fullName", target = "fullName")
    @Mapping(source = "avatarPath", target = "avatarPath", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @BeanMapping(ignoreByDefault = true)
    void mappingUpdateProfileAdminFormToEntity(UpdateProfileAdminForm form, @MappingTarget Account account);
}
