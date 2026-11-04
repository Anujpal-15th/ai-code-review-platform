package com.mentorlens;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    record Error(String error) {}

    private static ResponseEntity<Error> of(HttpStatus s, String msg) {
        return ResponseEntity.status(s).body(new Error(msg));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Error> invalid(MethodArgumentNotValidException e) {
        var f = e.getBindingResult().getFieldError();
        return of(HttpStatus.BAD_REQUEST, f == null ? "Invalid input" : f.getField() + " " + f.getDefaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Error> unreadable(HttpMessageNotReadableException e) {
        return of(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    // a concurrent duplicate registration hits the unique email constraint
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Error> conflict(DataIntegrityViolationException e) {
        return of(HttpStatus.BAD_REQUEST, "Email already registered");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Error> status(ResponseStatusException e) {
        return of(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason());
    }
}
