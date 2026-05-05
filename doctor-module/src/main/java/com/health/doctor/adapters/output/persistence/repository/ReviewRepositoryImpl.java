package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.health.common.redis.RedisUtil;
import com.health.doctor.domain.model.Review;
import com.health.doctor.domain.ports.ReviewRepositoryPort;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.*;

@Slf4j
@Singleton
public class ReviewRepositoryImpl implements ReviewRepositoryPort {

    private final CqlSession session;
    private final RedisUtil redisUtil;

    private final PreparedStatement insertReview;
    private final PreparedStatement selectReviewsByDoctor;
    
    private static final String CACHE_RATING_PREFIX = "doctor-rating:";

    public ReviewRepositoryImpl(CqlSession session, RedisUtil redisUtil) {
        this.session = session;
        this.redisUtil = redisUtil;

        insertReview = session.prepare(
                insertInto("doctor_service", "doctor_reviews")
                        .value("doctor_id", bindMarker("doctor_id"))
                        .value("patient_id", bindMarker("patient_id"))
                        .value("patient_name", bindMarker("patient_name"))
                        .value("rating", bindMarker("rating"))
                        .value("comment", bindMarker("comment"))
                        .value("created_at", bindMarker("created_at"))
                        .build()
        );

        selectReviewsByDoctor = session.prepare(
                selectFrom("doctor_service", "doctor_reviews")
                        .all()
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );
    }

    @Override
    public void save(Review review) {
        session.execute(insertReview.bind()
                .setUuid("doctor_id", review.getDoctorId())
                .setUuid("patient_id", review.getPatientId())
                .setString("patient_name", review.getPatientName())
                .setInt("rating", review.getRating())
                .setString("comment", review.getComment())
                .setInstant("created_at", review.getCreatedAt() != null ? review.getCreatedAt() : Instant.now())
        );
        redisUtil.deleteAsync(CACHE_RATING_PREFIX + review.getDoctorId());
    }

    @Override
    public List<Review> findByDoctorId(UUID doctorId) {
        ResultSet rs = session.execute(selectReviewsByDoctor.bind().setUuid("doctor_id", doctorId));
        List<Review> reviews = new ArrayList<>();
        for (Row row : rs) {
            reviews.add(new Review(
                    row.getUuid("doctor_id"),
                    row.getUuid("patient_id"),
                    row.getString("patient_name"),
                    row.getInt("rating"),
                    row.getString("comment"),
                    row.getInstant("created_at")
            ));
        }
        return reviews;
    }

    @Override
    public double getAverageRating(UUID doctorId) {
        String cacheKey = CACHE_RATING_PREFIX + doctorId;
        String cached = redisUtil.get(cacheKey, String.class);
        if (cached != null) {
            return Double.parseDouble(cached);
        }

        List<Review> reviews = findByDoctorId(doctorId);
        if (reviews.isEmpty()) return 0.0;

        double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        redisUtil.setAsync(cacheKey, String.valueOf(avg), 3600); // 1h
        return avg;
    }
}
