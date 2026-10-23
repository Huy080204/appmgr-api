package com.appmgr.api.model.criteria;

import com.appmgr.api.model.Account;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AccountCriteria implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private int kind;
    private String username;
    private Integer status;
    private String email;
    private String fullName;
    private String phone;

    @Schema(hidden = true)
    public Specification<Account> getSpecification() {
        return new Specification<Account>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Predicate toPredicate(Root<Account> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();

                if (getId() != null) {
                    predicates.add(cb.equal(root.get("id"), getId()));
                }
                if (getKind() > 0) {
                    predicates.add(cb.equal(root.get("kind"), getKind()));
                }
                if (getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), getStatus()));
                }
                if (StringUtils.hasText(getUsername())) {
                    predicates.add(cb.like(cb.lower(root.get("username")), "%" + getUsername().toLowerCase() + "%"));
                }
                if (StringUtils.hasText(getEmail())) {
                    predicates.add(cb.like(cb.lower(root.get("email")), "%" + getEmail().toLowerCase() + "%"));
                }
                if (StringUtils.hasText(getFullName())) {
                    predicates.add(cb.like(cb.lower(root.get("fullName")), "%" + getFullName().toLowerCase() + "%"));
                }
                return cb.and(predicates.toArray(new Predicate[predicates.size()]));
            }
        };
    }
}
