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
@Table(name = DatabaseConstant.PREFIX_TABLE + "project")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Project extends Auditable<String> {
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
