package com.health.doctor.domain.model;

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

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getGeohash() {
        return geohash;
    }

    public void setGeohash(String geohash) {
        this.geohash = geohash;
    }

    public String getLocationText() {
        return locationText;
    }

    public void setLocationText(String locationText) {
        this.locationText = locationText;
    }
}
