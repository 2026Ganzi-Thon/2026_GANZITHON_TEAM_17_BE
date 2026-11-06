package com.ganzithon.wool.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AgeGroupConverter implements AttributeConverter<AgeGroup, String> {

    @Override
    public String convertToDatabaseColumn(AgeGroup attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public AgeGroup convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AgeGroup.from(dbData);
    }
}
