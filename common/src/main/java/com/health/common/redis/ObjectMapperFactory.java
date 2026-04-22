package com.health.common.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class ObjectMapperFactory {

    @Singleton
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Optional: configure mapper
        mapper.enable(SerializationFeature.INDENT_OUTPUT); // pretty print
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}