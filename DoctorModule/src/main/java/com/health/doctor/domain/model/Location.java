package com.health.doctor.domain.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
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

}
