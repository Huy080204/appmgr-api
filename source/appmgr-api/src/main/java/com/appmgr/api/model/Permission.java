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
@Table(name = DatabaseConstant.PREFIX_TABLE + "permission")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Permission extends Auditable<String> {
    @Column(name = "name")
    private String name;
    @Column(name = "action")
    private String action;
    @Column(name = "show_menu")
    private Boolean showMenu;
    private String description;
    @Column(name = "p_code")
    private String pCode;
    @Column(name = "name_group")
    private String nameGroup;
}
