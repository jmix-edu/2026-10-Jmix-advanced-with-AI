package com.company.timesheets.datatype;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Spent time must be more than {@code 0:00}; {@code null} is left to {@code @NotNull}.
 * <p>
 * Put it in the {@code UiComponentChecks} group: then the form shows the error under the field, while JPA
 * (which validates only the default group on persist) leaves the same rule to {@code TimeEntryRulesListener}.
 */
@Documented
@Constraint(validatedBy = PositiveSpentTimeValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PositiveSpentTime {

    String message() default "{msg://com.company.timesheets.datatype/PositiveSpentTime.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
