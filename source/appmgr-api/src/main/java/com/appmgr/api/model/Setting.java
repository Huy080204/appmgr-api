package com.appmgr.api.model;

import com.appmgr.api.constant.DatabaseConstant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.Table;

@Entity
@Table(name = DatabaseConstant.PREFIX_TABLE + "setting")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Setting extends Auditable<String> {
    @Column(name = "group_name")
    private String groupName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "key_name")
    private String keyName;

    @Column(name = "value_data", columnDefinition = "TEXT")
    private String valueData;

    @Column(name = "data_type")
    private String dataType; // Integer, String, Boolean, Double, RichText, Select, Upload

    @Column(name = "options", columnDefinition = "LONGTEXT")
    private String option;

    @Column(name = "is_system")
    private Boolean isSystem;
}
