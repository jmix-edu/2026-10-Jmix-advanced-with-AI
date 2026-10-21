package com.company.timesheets.component;

import com.company.timesheets.datatype.SpentTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Value conversion of {@link SpentTimeField}, from specs/02_ui_development/04-spent-time-field.spec.adoc.
 */
class SpentTimeFieldTest {

    @Test
    void hoursAndMinutesBecomeSpentTime() {
        assertThat(SpentTimeField.toSpentTime(1, 30)).isEqualTo(new SpentTime(90));
        assertThat(SpentTimeField.toSpentTime(0, 0)).isEqualTo(new SpentTime(0));
        assertThat(SpentTimeField.toSpentTime(24, 59)).isEqualTo(new SpentTime(24 * 60 + 59));
    }

    @Test
    void anEmptyPartCountsAsZeroAndBothEmptyIsNoValue() {
        assertThat(SpentTimeField.toSpentTime(null, 45)).isEqualTo(new SpentTime(45));
        assertThat(SpentTimeField.toSpentTime(2, null)).isEqualTo(new SpentTime(120));
        assertThat(SpentTimeField.toSpentTime(null, null)).isNull();
    }

    @Test
    void partsOutOfRangeAreRejected() {
        assertThatThrownBy(() -> SpentTimeField.toSpentTime(0, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpentTimeField.toSpentTime(0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpentTimeField.toSpentTime(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpentTimeField.toSpentTime(25, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spentTimeIsSplitIntoHoursAndMinutes() {
        assertThat(SpentTimeField.toParts(new SpentTime(90))).isEqualTo(new SpentTimeField.Parts(1, 30));
        assertThat(SpentTimeField.toParts(new SpentTime(0))).isEqualTo(new SpentTimeField.Parts(0, 0));
        assertThat(SpentTimeField.toParts(null)).isEqualTo(new SpentTimeField.Parts(null, null));
    }

    @Test
    void quickButtonsAddWithCarryIntoHours() {
        assertThat(SpentTimeField.plus(null, 15)).isEqualTo(new SpentTime(15));
        assertThat(SpentTimeField.plus(new SpentTime(45), 15)).isEqualTo(new SpentTime(60));
        assertThat(SpentTimeField.toParts(SpentTimeField.plus(new SpentTime(45), 15)))
                .isEqualTo(new SpentTimeField.Parts(1, 0));
        assertThat(SpentTimeField.plus(new SpentTime(90), 60)).isEqualTo(new SpentTime(150));
    }
}
