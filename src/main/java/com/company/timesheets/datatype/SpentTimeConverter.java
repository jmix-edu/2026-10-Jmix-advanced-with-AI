package com.company.timesheets.datatype;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Хранит {@link SpentTime} как число минут.
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
