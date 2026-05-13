package com.health.doctor.domain.ports;

import com.health.doctor.domain.model.Review;

import java.util.List;
import java.util.UUID;

public interface ReviewRepositoryPort {
    void save(Review review);
    List<Review> findByDoctorId(UUID doctorId);
    double getAverageRating(UUID doctorId);
    RatingSummary getRatingSummary(UUID doctorId);

    record RatingSummary(double averageRating, int reviewCount) {}
}
