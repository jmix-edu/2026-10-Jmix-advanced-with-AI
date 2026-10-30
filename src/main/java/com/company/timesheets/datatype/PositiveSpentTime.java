package com.company.timesheets.datatype;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Затраченное время должно быть больше {@code 0:00}; {@code null} проверяет {@code @NotNull}.
 * <p>
 * Входит в группу {@code UiComponentChecks}: тогда форма показывает ошибку под полем, а JPA
 * (при persist проверяет только группу по умолчанию) оставляет это правило {@code TimeEntryRulesListener}.
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
