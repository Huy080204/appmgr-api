package com.appmgr.api.repository;

import com.appmgr.api.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {
    Boolean existsByUsername(String username);

    Account findAccountByUsername(String username);

    Optional<Account> findAccountByEmail(String email);

    Optional<Account> findAccountByPhone(String phone);

    Boolean existsByPhoneAndStatusNot(String phone, int status);

    Boolean existsByEmailAndStatusNot(String email, int status);

    Optional<Account> findByIdAndStatus(Long id, Integer status);

    @Query("SELECT a FROM Account a WHERE (a.username=:username OR a.phone=:username OR a.email=:username) AND a.status=:status")
    Optional<Account> findFirstByUsernameOrEmailOrPhoneAndStatus(@Param("username") String username, @Param("status") Integer status);
}
