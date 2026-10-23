package com.appmgr.api.model.criteria;

import com.appmgr.api.model.Setting;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
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
public class SettingCriteria implements Serializable {
    private Long id;
    private String groupName;
    private String keyName;
    private String valueData;
    private String dataType;
    private Boolean isSystem;
    private Integer status;

    @Schema(hidden = true)
    public Specification<Setting> getCriteria() {
        return new Specification<Setting>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Predicate toPredicate(Root<Setting> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();
                if (getId() != null) {
                    predicates.add(cb.equal(root.get("id"), getId()));
                }
                if (StringUtils.isNotBlank(getGroupName())) {
                    predicates.add(cb.like(cb.lower(root.get("groupName")), "%" + getGroupName().toLowerCase() + "%"));
                }
                if (StringUtils.isNotBlank(getKeyName())) {
                    predicates.add(cb.equal(root.get("keyName"), getKeyName()));
                }
                if (StringUtils.isNotBlank(getValueData())) {
                    predicates.add(cb.like(cb.lower(root.get("valueData")), "%" + getValueData().toLowerCase() + "%"));
                }
                if (StringUtils.isNotBlank(getDataType())) {
                    predicates.add(cb.like(cb.lower(root.get("dataType")), "%" + getDataType().toLowerCase() + "%"));
                }
                if (getIsSystem() != null) {
                    predicates.add(cb.equal(root.get("isSystem"), getIsSystem()));
                }
                if (getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), getStatus()));
                }
                return cb.and(predicates.toArray(new Predicate[0]));
            }
        };
    }
}
