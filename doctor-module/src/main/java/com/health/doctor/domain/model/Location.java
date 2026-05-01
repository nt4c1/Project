package com.health.doctor.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class Location {
    private double latitude;
    private double longitude;
    private String geohash;
    private String locationText;

    public Location(double latitude, double longitude, String geohash, String locationText) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.geohash = geohash;
        this.locationText = locationText;
    }

    public Location(String locationText) {
        this.locationText=locationText;
    }
}
