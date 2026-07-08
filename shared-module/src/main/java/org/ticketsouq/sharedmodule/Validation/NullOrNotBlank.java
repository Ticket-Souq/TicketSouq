package org.ticketsouq.sharedmodule.Validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = NullOrNotBlankValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NullOrNotBlank {

    String message() default "must be null or contain non-whitespace characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}