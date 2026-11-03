package com.appmgr.api.model.criteria;

import com.appmgr.api.model.Channel;
import com.appmgr.api.model.Version;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ChannelCriteria implements Serializable {

    private Long id;
    private String name;
    private Integer status;
    private Long applicationId;

    @Schema(hidden = true)
    public Specification<Channel> getCriteria() {
        return new Specification<Channel>() {
            private static final long serialVersionUID = 1L;
            @Override
            public Predicate toPredicate(Root<Channel> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
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

                if (getApplicationId() != null) {
                    Subquery<Long> versionSubquery = query.subquery(Long.class);
                    Root<Version> versionRoot = versionSubquery.from(Version.class);
                    versionSubquery.select(versionRoot.get("id"))
                            .where(cb.equal(versionRoot.get("channel"), root),
                                    cb.equal(versionRoot.get("application").get("id"), getApplicationId()));
                    predicates.add(cb.exists(versionSubquery));
                }
                return cb.and(predicates.toArray(new Predicate[predicates.size()]));
            }
        };
    }
}
