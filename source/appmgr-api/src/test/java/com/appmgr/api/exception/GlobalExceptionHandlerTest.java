package com.appmgr.api.exception;

import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.form.ErrorForm;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpHeaders headers = new HttpHeaders();
    private final WebRequest request = mock(WebRequest.class);

    @Test
    @SuppressWarnings("unchecked")
    void shouldAnswerAppBodyWith404WhenNoHandlerFound() {
        NoHandlerFoundException exception = new NoHandlerFoundException("GET", "/v1/unknown", headers);

        ResponseEntity<Object> response = handler.handleNoHandlerFoundException(exception, headers, HttpStatusCode.valueOf(404), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiMessageDto<String> body = (ApiMessageDto<String>) response.getBody();
        assertThat(body.getResult()).isFalse();
        assertThat(body.getCode()).isEqualTo("ERROR handleNoHandlerFoundException");
        assertThat(body.getMessage()).isEqualTo("[Ex3]: 404");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAnswerSameBodyWith404WhenNoResourceFound() {
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/v1/unknown");

        ResponseEntity<Object> response = handler.handleNoResourceFoundException(exception, headers, HttpStatusCode.valueOf(404), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiMessageDto<String> body = (ApiMessageDto<String>) response.getBody();
        assertThat(body.getResult()).isFalse();
        assertThat(body.getCode()).isEqualTo("ERROR handleNoHandlerFoundException");
        assertThat(body.getMessage()).isEqualTo("[Ex3]: 404");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAnswerFieldErrorsWith400WhenBodyIsNotValid() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "form");
        bindingResult.addError(new FieldError("form", "username", "must not be blank"));
        bindingResult.addError(new FieldError("form", "groupId", "must not be null"));
        MethodParameter parameter = new MethodParameter(GlobalExceptionHandlerTest.class.getDeclaredMethod("endpoint", Object.class), 0);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(exception, headers, HttpStatusCode.valueOf(400), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiMessageDto<List<ErrorForm>> body = (ApiMessageDto<List<ErrorForm>>) response.getBody();
        assertThat(body.getResult()).isFalse();
        assertThat(body.getCode()).isEqualTo("ERROR");
        assertThat(body.getMessage()).isEqualTo("username: must not be blank, groupId: must not be null");
        assertThat(body.getData()).extracting(ErrorForm::getField).containsExactly("username", "groupId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAnswerGenericMessageWith400WhenJsonIsMalformed() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("JSON parse error", mock(HttpInputMessage.class));

        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(exception, headers, HttpStatusCode.valueOf(400), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiMessageDto<String> body = (ApiMessageDto<String>) response.getBody();
        assertThat(body.getResult()).isFalse();
        assertThat(body.getCode()).isEqualTo("ERROR");
        assertThat(body.getMessage()).isEqualTo("Malformed request body");
    }

    @SuppressWarnings("unused")
    private void endpoint(Object form) {
    }
}
