package com.health.doctor.adapters.output.persistence.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
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
    private final PreparedStatement insertReviewByTime;
    private final PreparedStatement updateRatingSummary;
    private final PreparedStatement selectReviewsByDoctor;
    private final PreparedStatement selectRatingSummary;
    
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

        insertReviewByTime = session.prepare(
                insertInto("doctor_service", "doctor_reviews_by_time")
                        .value("doctor_id", bindMarker("doctor_id"))
                        .value("created_at", bindMarker("created_at"))
                        .value("patient_id", bindMarker("patient_id"))
                        .value("patient_name", bindMarker("patient_name"))
                        .value("rating", bindMarker("rating"))
                        .value("comment", bindMarker("comment"))
                        .build()
        );

        updateRatingSummary = session.prepare(
                update("doctor_service", "doctor_rating_summary")
                        .increment("total_rating", bindMarker("rating_increment"))
                        .increment("review_count")
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        selectReviewsByDoctor = session.prepare(
                selectFrom("doctor_service", "doctor_reviews_by_time")
                        .all()
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );

        selectRatingSummary = session.prepare(
                selectFrom("doctor_service", "doctor_rating_summary")
                        .all()
                        .whereColumn("doctor_id").isEqualTo(bindMarker("doctor_id"))
                        .build()
        );
    }

    @Override
    public void save(Review review) {
        Instant now = review.getCreatedAt() != null ? review.getCreatedAt() : Instant.now();
        
        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED)
                .addStatement(insertReview.bind()
                        .setUuid("doctor_id", review.getDoctorId())
                        .setUuid("patient_id", review.getPatientId())
                        .setString("patient_name", review.getPatientName())
                        .setInt("rating", review.getRating())
                        .setString("comment", review.getComment())
                        .setInstant("created_at", now))
                .addStatement(insertReviewByTime.bind()
                        .setUuid("doctor_id", review.getDoctorId())
                        .setInstant("created_at", now)
                        .setUuid("patient_id", review.getPatientId())
                        .setString("patient_name", review.getPatientName())
                        .setInt("rating", review.getRating())
                        .setString("comment", review.getComment()));

        session.execute(batch.build());

        // Counter updates cannot be part of a logged batch in Cassandra/ScyllaDB if they are on different partitions
        // but here they are on different tables anyway. Counters usually have their own batch restrictions.
        // Actually, you can't mix counter updates with regular updates in a logged batch.
        session.execute(updateRatingSummary.bind()
                .setLong("rating_increment", (long) review.getRating())
                .setUuid("doctor_id", review.getDoctorId()));

        redisUtil.deleteAsync(CACHE_RATING_PREFIX + review.getDoctorId() + ":summary");
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
        return getRatingSummary(doctorId).averageRating();
    }

    @Override
    public RatingSummary getRatingSummary(UUID doctorId) {
        String cacheKey = CACHE_RATING_PREFIX + doctorId + ":summary";
        RatingSummary cached = redisUtil.get(cacheKey, RatingSummary.class);
        if (cached != null) return cached;

        Row summary = session.execute(selectRatingSummary.bind().setUuid("doctor_id", doctorId)).one();
        if (summary == null) return new RatingSummary(0.0, 0);

        long totalRating = summary.getLong("total_rating");
        long reviewCount = summary.getLong("review_count");

        if (reviewCount == 0) return new RatingSummary(0.0, 0);

        double avg = (double) totalRating / reviewCount;
        RatingSummary result = new RatingSummary(avg, (int) reviewCount);
        redisUtil.setAsync(cacheKey, result, 3600); // 1h
        return result;
    }
}
