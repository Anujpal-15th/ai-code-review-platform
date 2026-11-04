package com.mentorlens;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Rejects NUL characters, which PostgreSQL text columns cannot store. */
@Pattern(regexp = "[^\\x00]*")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoNul {
    String message() default "must not contain NUL characters";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
