package com.health.doctor.application.service;

import com.health.doctor.adapters.output.client.PhotonClient;
import com.health.doctor.adapters.output.client.PhotonResponse;
import com.health.doctor.adapters.output.client.GeoHashUtil;
import com.health.doctor.domain.model.Location;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Singleton
public class LocationService {

    private final PhotonClient photonClient;

    public LocationService(PhotonClient photonClient) {
        this.photonClient = photonClient;
    }

    public Location resolve(String text) {
        log.info("Resolving location for text: {}", text);

        PhotonResponse response = photonClient.search(text);
        if (response == null) {
            log.error("PhotonClient returned null response");
            throw new RuntimeException("Photon service returned null");
        }

        if (response.getFeatures() == null || response.getFeatures().isEmpty()) {
            log.warn("No features found in Photon response for '{}'", text);
            throw new RuntimeException("No location found for: " + text);
        }

        PhotonResponse.Feature firstFeature = response.getFeatures().getFirst();
        log.debug("First feature retrieved: {}", firstFeature);

        if (firstFeature.getGeometry() == null) {
            log.error("Feature geometry is null");
            throw new RuntimeException("Invalid geometry in location result");
        }

        if (firstFeature.getGeometry().getCoordinates() == null || firstFeature.getGeometry().getCoordinates().size() < 2) {
            log.error("Feature coordinates are invalid: {}", firstFeature.getGeometry().getCoordinates());
            throw new RuntimeException("Invalid geometry coordinates in location result");
        }

        // Photon returns [lng, lat]
        double lng = firstFeature.getGeometry().getCoordinates().get(0);
        double lat = firstFeature.getGeometry().getCoordinates().get(1);
        log.info("Coordinates found: lat={}, lng={}", lat, lng);

        String geohash = GeoHashUtil.generate(lat, lng, 5);
        log.info("Generated geohash: {}", geohash);

        PhotonResponse.Properties props = firstFeature.getProperties();
        String fullName = Stream.of(
                props.getName(),
                props.getDistrict(),
                props.getCity(),
                props.getState(),
                props.getCountry()
        )
                .filter(s -> s!= null && !s.isBlank())
                .collect(Collectors.joining(","));

        log.info("Resolved full location name: {}", fullName);

        return new Location(lat, lng, geohash, fullName);
    }

    // Helper to avoid nulls in fullName
    private String safe(String value) {
        return value != null ? value : "";
    }
}