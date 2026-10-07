package com.company.timesheets.datatype;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpentTimeDatatypeTest {

    private final SpentTimeDatatype datatype = new SpentTimeDatatype();
    private final SpentTimeConverter converter = new SpentTimeConverter();

    @ParameterizedTest
    @CsvSource({
            "0, 0:00",
            "5, 0:05",
            "150, 2:30",
            "600, 10:00",
            "6000, 100:00"
    })
    void formatsAsHoursAndTwoDigitMinutes(long minutes, String expected) {
        assertThat(datatype.format(new SpentTime(minutes))).isEqualTo(expected);
        assertThat(datatype.format(new SpentTime(minutes), Locale.GERMANY)).isEqualTo(expected);
    }

    @Test
    void formatsNullAsEmptyString() {
        assertThat(datatype.format(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2:30|150",
            "' 2:30 '|150",
            "0:05|5",
            "0:5|5",
            "2|120"
    })
    void parsesHoursAndMinutes(String text, long expectedMinutes) throws ParseException {
        assertThat(datatype.parse(text)).isEqualTo(new SpentTime(expectedMinutes));
        assertThat(datatype.parse(text, Locale.GERMANY)).isEqualTo(new SpentTime(expectedMinutes));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void parsesBlankAsNull(String text) throws ParseException {
        assertThat(datatype.parse(text)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2:60", "2:75", "-1:00", "abc", "2:", ":30", "2:30:00", "2.5", "99999999999999999999"})
    void rejectsInvalidInput(String text) {
        assertThatThrownBy(() -> datatype.parse(text)).isInstanceOf(ParseException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 59, 60, 61, 150, 6000})
    void parsesWhatItFormats(long minutes) throws ParseException {
        SpentTime value = new SpentTime(minutes);
        assertThat(datatype.parse(datatype.format(value))).isEqualTo(value);
    }

    @Test
    void survivesJavaSerialization() throws Exception {
        SpentTime value = new SpentTime(150);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(in.readObject()).isEqualTo(value);
        }
    }

    @Test
    void converterStoresMinutes() {
        assertThat(converter.convertToDatabaseColumn(new SpentTime(150))).isEqualTo(150L);
        assertThat(converter.convertToEntityAttribute(150L)).isEqualTo(new SpentTime(150));
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
