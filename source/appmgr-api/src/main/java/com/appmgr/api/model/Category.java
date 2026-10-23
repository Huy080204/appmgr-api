package com.appmgr.api.model;

import com.appmgr.api.constant.DatabaseConstant;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = DatabaseConstant.PREFIX_TABLE + "category")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Category extends Auditable<String> {
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
