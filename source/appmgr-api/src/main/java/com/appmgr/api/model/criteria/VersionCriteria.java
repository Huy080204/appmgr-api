package com.appmgr.api.model.criteria;

import com.appmgr.api.model.Version;
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
public class VersionCriteria implements Serializable {

    private Long id;
    private Integer status;
    private Long applicationId;
    private Long categoryId;
    private Long channelId;
    private Integer type;

    @Schema(hidden = true)
    public Specification<Version> getCriteria() {
        return new Specification<Version>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Predicate toPredicate(Root<Version> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();

                if (getId() != null) {
                    predicates.add(cb.equal(root.get("id"), getId()));
                }

                if (getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), getStatus()));
                }

                if (getApplicationId() != null) {
                    predicates.add(cb.equal(root.get("application").get("id"), getApplicationId()));
                }

                if (getCategoryId() != null) {
                    predicates.add(cb.equal(root.get("category").get("id"), getCategoryId()));
                }

                if (getChannelId() != null) {
                    predicates.add(cb.equal(root.get("channel").get("id"), getChannelId()));
                }

                if (getType() != null) {
                    predicates.add(cb.equal(root.get("type"), getType()));
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            }
        };
    }
}
