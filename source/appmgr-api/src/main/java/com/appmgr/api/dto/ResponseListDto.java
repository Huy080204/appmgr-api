package com.appmgr.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class ResponseListDto<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private T content;
    private Long totalElements;
    private Integer totalPages;
}
