package com.appmgr.api.model.criteria;

import com.appmgr.api.model.Application;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ApplicationCriteria implements Serializable {

    private Long id;
    private Integer status;
    private String name;
    private Long projectId;

    @Schema(hidden = true)
    public Specification<Application> getCriteria() {
        return new Specification<Application>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Predicate toPredicate(Root<Application> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                if (getId() != null) {
                    predicates.add(cb.equal(root.get("id"), getId()));
                }

                if (getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), getStatus()));
                }

                if (StringUtils.hasText(getName())) {
                    predicates.add(cb.like(cb.lower(root.get("name")), "%" + getName().toLowerCase() + "%"));
                }

                if (getProjectId() != null) {
                    Join<Application, Object> joinProject = root.join("project", JoinType.INNER);
                    predicates.add(cb.equal(joinProject.get("id"), getProjectId()));
                }
                return cb.and(predicates.toArray(new Predicate[predicates.size()]));
            }
        };
    }
}
