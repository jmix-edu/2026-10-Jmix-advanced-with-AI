package com.company.timesheets.datatype;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link SpentTime} as a number of minutes.
 */
@Converter(autoApply = true)
public class SpentTimeConverter implements AttributeConverter<SpentTime, Long> {

    @Override
    public Long convertToDatabaseColumn(SpentTime attribute) {
        return attribute == null ? null : attribute.minutes();
    }

    @Override
    public SpentTime convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : new SpentTime(dbData);
    }
}
