package com.company.timesheets.datatype;

import io.jmix.core.metamodel.annotation.DatatypeDef;
import io.jmix.core.metamodel.annotation.Ddl;
import io.jmix.core.metamodel.datatype.Datatype;
import org.jspecify.annotations.Nullable;

import java.text.ParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats {@link SpentTime} as {@code hours:minutes} and parses {@code 2:30}, {@code 0:5} or bare hours {@code 2}.
 * The format does not depend on the locale.
 */
@DatatypeDef(id = "spentTime",
        javaClass = SpentTime.class,
        defaultForClass = true)
@Ddl("bigint")
public class SpentTimeDatatype implements Datatype<SpentTime> {

    private static final Pattern PATTERN = Pattern.compile("(\\d+)(?::(\\d{1,2}))?");

    @Override
    public String format(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        long minutes = ((SpentTime) value).minutes();
        return "%d:%02d".formatted(minutes / 60, minutes % 60);
    }

    @Override
    public String format(@Nullable Object value, Locale locale) {
        return format(value);
    }

    @Nullable
    @Override
    public SpentTime parse(@Nullable String value) throws ParseException {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        Matcher matcher = PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new ParseException("Expected hours:minutes, got '" + text + "'", 0);
        }
        int minutes = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        if (minutes > 59) {
            throw new ParseException("Minutes must be at most 59, got '" + text + "'", text.indexOf(':') + 1);
        }
        try {
            long hours = Long.parseLong(matcher.group(1));
            return new SpentTime(Math.addExact(Math.multiplyExact(hours, 60), minutes));
        } catch (ArithmeticException | NumberFormatException e) {
            throw new ParseException("Too many hours: '" + text + "'", 0);
        }
    }

    @Nullable
    @Override
    public SpentTime parse(@Nullable String value, Locale locale) throws ParseException {
        return parse(value);
    }
}
