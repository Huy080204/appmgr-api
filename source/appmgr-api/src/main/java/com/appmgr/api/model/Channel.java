package com.appmgr.api.model;

import com.appmgr.api.constant.DatabaseConstant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.Table;

@Entity
@Table(name = DatabaseConstant.PREFIX_TABLE + "channel")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Channel extends Auditable<String> {
    private String name;
}
