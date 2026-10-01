package com.appmgr.api.form;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ErrorForm {
    private String field;
    private String message;
}
