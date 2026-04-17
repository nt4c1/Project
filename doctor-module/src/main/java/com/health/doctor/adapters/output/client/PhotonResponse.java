package com.health.doctor.adapters.output.client;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@Introspected
@Serdeable
@Serdeable.Deserializable
public class PhotonResponse {

    private String type;
    private List<Feature> features;
    @Setter
    @Getter
    @Serdeable
    @Introspected
    @Serdeable.Deserializable
    public static class Feature {
        private String type;
        private Geometry geometry;
        private Properties properties;

    }

    @Setter
    @Getter
    @Serdeable
    @Introspected
    @Serdeable.Deserializable
    public static class Geometry {
        private String type;
        private List<Double> coordinates; // [lng, lat]

    }

    @Setter
    @Getter
    @Serdeable
    @Introspected
    @Serdeable.Deserializable
    public static class Properties {
        private String name;
        private String district;
        private String city;
        private String country;
        private String state;

    }
}