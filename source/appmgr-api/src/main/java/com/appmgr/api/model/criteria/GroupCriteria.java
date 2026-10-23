package com.appmgr.api.model.criteria;

import com.appmgr.api.model.Group;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GroupCriteria implements Serializable {
    private Long id;
    private String name;
    private Integer kind;
    private Integer status;
    private Boolean isSystemRole;

    private Integer excludeKind;
    private List<Integer> kinds;

    @Schema(hidden = true)
    public Specification<Group> getSpecification() {
        return new Specification<Group>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Predicate toPredicate(Root<Group> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                if (getId() != null) {
                    predicates.add(cb.equal(root.get("id"), getId()));
                }
                if (getName() != null) {
                    predicates.add(cb.like(cb.lower(root.get("name")), "%" + getName().toLowerCase() + "%"));
                }
                if (getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), getStatus()));
                }
                if (getKind() != null) {
                    predicates.add(cb.equal(root.get("kind"), getKind()));
                }
                if (getIsSystemRole() != null) {
                    predicates.add(cb.equal(root.get("isSystemRole"), getIsSystemRole()));
                }
                if (getExcludeKind() != null) {
                    predicates.add(cb.notEqual(root.get("kind"), getExcludeKind()));
                }
                if (getKinds() != null && !getKinds().isEmpty()) {
                    predicates.add(root.get("kind").in(getKinds()));
                }
                return cb.and(predicates.toArray(new Predicate[predicates.size()]));
            }
        };
    }
}
