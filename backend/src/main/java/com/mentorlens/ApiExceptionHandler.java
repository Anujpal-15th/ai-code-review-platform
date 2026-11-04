package com.mentorlens;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

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

    // a malformed id can never match a submission
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Error> badId(MethodArgumentTypeMismatchException e) {
        return of(HttpStatus.NOT_FOUND, "Submission not found");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Error> status(ResponseStatusException e) {
        return of(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Error> mediaType(HttpMediaTypeNotSupportedException e) {
        return of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Error> method(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(e.getHeaders())
            .body(new Error("Method not allowed"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> other(Exception e) {
        // Spring's own client errors, such as 404 for an unknown path, keep their status
        if (e instanceof ErrorResponse r && r.getStatusCode().is4xxClientError()) {
            return of(HttpStatus.valueOf(r.getStatusCode().value()), "Request could not be processed");
        }
        log.error("Request failed", e);
        return of(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");
    }
}
