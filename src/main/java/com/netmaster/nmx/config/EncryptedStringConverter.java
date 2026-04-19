package com.netmaster.nmx.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static TextEncryptor textEncryptor;

    @Autowired
    public void setTextEncryptor(TextEncryptor textEncryptor) {
        EncryptedStringConverter.textEncryptor = textEncryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return textEncryptor != null ? textEncryptor.encrypt(attribute) : attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return textEncryptor != null ? textEncryptor.decrypt(dbData) : dbData;
    }
}
