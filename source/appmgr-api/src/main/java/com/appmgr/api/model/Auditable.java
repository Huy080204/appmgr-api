package com.appmgr.api.model;

import com.appmgr.api.constant.DatabaseConstant;import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import java.util.Date;

@MappedSuperclass
@Getter
@Setter
public abstract class Auditable<T> extends ReuseId {
    @Id
    @GeneratedValue(generator = DatabaseConstant.APP_ID_GENERATOR_NAME)
    @GenericGenerator(name = DatabaseConstant.APP_ID_GENERATOR_NAME, strategy = DatabaseConstant.APP_ID_GENERATOR_STRATEGY)
    private Long id;
    
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private T createdBy;

    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private Date createdDate;

    @LastModifiedBy
    @Column(name = "modified_by", nullable = false)
    private T modifiedBy;

    @LastModifiedDate
    @Column(name = "modified_date", nullable = false)
    private Date modifiedDate;

    private int status = 1;
}
