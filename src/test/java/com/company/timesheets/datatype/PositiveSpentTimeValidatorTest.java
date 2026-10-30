package com.company.timesheets.datatype;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code 0:00} — ошибка поля затраченного времени; {@code null} оставлен на {@code @NotNull}.
 */
class PositiveSpentTimeValidatorTest {

    private final PositiveSpentTimeValidator validator = new PositiveSpentTimeValidator();

    @Test
    void zeroIsInvalidAnyPositiveTimeAndNullAreValid() {
        assertThat(validator.isValid(new SpentTime(0), null)).isFalse();
        assertThat(validator.isValid(new SpentTime(1), null)).isTrue();
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
