package com.company.timesheets.datatype;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Checks {@link PositiveSpentTime}.
 */
public class PositiveSpentTimeValidator implements ConstraintValidator<PositiveSpentTime, SpentTime> {

    @Override
    public boolean isValid(SpentTime value, ConstraintValidatorContext context) {
        return value == null || value.minutes() > 0;
    }
}
