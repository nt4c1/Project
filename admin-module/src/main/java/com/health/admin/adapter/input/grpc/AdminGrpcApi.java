package com.health.admin.adapter.input.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.admin.application.AdminInterface;
import com.health.common.redis.RedisUtil;
import com.health.grpc.admin.*;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenRequest;
import com.health.grpc.auth.ValidateTokenResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micronaut.grpc.annotation.GrpcService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.health.common.auth.JwtAuthInterceptor.ROLE_KEY;

@Slf4j
@GrpcService
public class AdminGrpcApi extends AdminGrpcServiceGrpc.AdminGrpcServiceImplBase {

    private final RedisUtil redisUtil;
    private final AdminInterface adminUseCase;
    private final ObjectMapper objectMapper;
    private static final String KEY_EVENTS = "stats:events";

    public AdminGrpcApi(RedisUtil redisUtil, AdminInterface adminUseCase, ObjectMapper objectMapper) {
        this.redisUtil = redisUtil;
        this.adminUseCase = adminUseCase;
        this.objectMapper = objectMapper;
    }

    private void ensureAdmin() {
        String role = ROLE_KEY.get();
        if (role == null || !"Admin".equalsIgnoreCase(role)) {
            throw new com.health.common.exception.DomainException("Access denied: Admins only", Status.PERMISSION_DENIED);
        }
    }

    @Override
    public void refreshToken(com.health.grpc.auth.RefreshTokenRequest request, StreamObserver<TokenResponse> observer) {
        handle(observer, () -> {
            TokenResponse response = adminUseCase.refreshToken(request.getRefreshToken());
            observer.onNext(response);
            observer.onCompleted();
        });
    }

    @Override
    public void validateAdminToken(ValidateTokenRequest request, StreamObserver<ValidateTokenResponse> observer) {
        handle(observer, () -> {
            ValidateTokenResponse response = adminUseCase.validateAdmin(request.getToken());
            observer.onNext(response);
            observer.onCompleted();
        });
    }

    @Override
    public void getSystemStats(GetStatsRequest request, StreamObserver<GetStatsResponse> observer) {
        log.info("Processing getSystemStats request");
        handle(observer, () -> {
            ensureAdmin();
            GetStatsResponse response = adminUseCase.getSystemStats();
            log.debug("Stats retrieved from DB: doctors={}, patients={}, clinics={}, appts={}",
                    response.getTotalDoctors(), response.getTotalPatients(), 
                    response.getTotalClinics(), response.getTotalAppointments());
            observer.onNext(response);
            observer.onCompleted();
        });
    }

    @Override
    public void getRecentEvents(GetEventsRequest request, StreamObserver<GetEventsResponse> observer) {
        log.info("Processing getRecentEvents request, limit={}", request.getLimit());
        handle(observer, () -> {
            ensureAdmin();
            int limit = request.getLimit() > 0 ? request.getLimit() : 20;
            List<String> rawEvents = redisUtil.lrange(KEY_EVENTS, 0, limit - 1);
            log.debug("Retrieved {} raw events from Redis", rawEvents.size());

            List<EventMessage> events = rawEvents.stream()
                    .map(json -> {
                        try {
                            Map<String, String> map = objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                            return EventMessage.newBuilder()
                                    .setTimestamp(map.getOrDefault("timestamp", ""))
                                    .setEventType(map.getOrDefault("event_type", "UNKNOWN"))
                                    .setDetails(map.getOrDefault("details", ""))
                                    .build();
                        } catch (Exception e) {
                            log.error("Error parsing event JSON: {}", json, e);
                            return EventMessage.newBuilder()
                                    .setDetails("Error parsing event: " + json)
                                    .build();
                        }
                    })
                    .collect(Collectors.toList());

            observer.onNext(GetEventsResponse.newBuilder()
                    .addAllEvents(events)
                    .build());
            observer.onCompleted();
        });
    }

    private <T> void handle(StreamObserver<T> observer, Runnable action) {
        try {
            action.run();
        } catch (com.health.common.exception.DomainException e) {
            log.warn("Domain error: {}", e.getMessage());
            observer.onError(e.getGrpcStatus()
                    .withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Unexpected error", e);
            observer.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage()).asRuntimeException());
        }
    }
}
