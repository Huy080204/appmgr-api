package com.appmgr.api.model.criteria;

import com.appmgr.api.model.Project;
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
public class ProjectCriteria implements Serializable {

    private Long id;
    private Integer status;
    private String name;

    @Schema(hidden = true)
    public Specification<Project> getCriteria() {
        return new Specification<Project>() {
            private static final long serialVersionUID = 1L;
            @Override
            public Predicate toPredicate(Root<Project> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                if (getId() != null) {
                    predicates.add(cb.equal(root.get("id"), getId()));
                }

                if (StringUtils.hasText(getName())) {
                    predicates.add(cb.like(cb.lower(root.get("name")), "%" + getName().toLowerCase() + "%"));
                }

                if (getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), getStatus()));
                }
                return cb.and(predicates.toArray(new Predicate[predicates.size()]));
            }
        };
    }
}
