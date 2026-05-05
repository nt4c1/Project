package com.health.admin.adapter.input.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.admin.application.AdminInterface;
import com.health.common.redis.RedisUtil;
import com.health.grpc.admin.*;
import com.health.grpc.auth.TokenResponse;
import com.health.grpc.auth.ValidateTokenRequest;
import com.health.grpc.auth.ValidateTokenResponse;
import com.health.grpc.auth.TokenRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micronaut.grpc.annotation.GrpcService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.health.common.auth.GrpcAuthInterceptor.EMAIL_KEY;
import static com.health.common.auth.GrpcAuthInterceptor.PASSWORD_KEY;

@Slf4j
@GrpcService
public class AdminGrpcApi extends AdminGrpcServiceGrpc.AdminGrpcServiceImplBase {

    private final RedisUtil redisUtil;
    private final AdminInterface adminUseCase;
    private final ObjectMapper objectMapper;
    private static final String KEY_TOTAL_DOCTORS = "stats:total:doctors";
    private static final String KEY_TOTAL_PATIENTS = "stats:total:patients";
    private static final String KEY_TOTAL_APPTS = "stats:total:appointments";
    private static final String KEY_EVENTS = "stats:events";

    public AdminGrpcApi(RedisUtil redisUtil, AdminInterface adminUseCase, ObjectMapper objectMapper) {
        this.redisUtil = redisUtil;
        this.adminUseCase = adminUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public void adminLogin(TokenRequest request, StreamObserver<TokenResponse> observer) {
        handle(observer, () -> {
            String email = EMAIL_KEY.get();
            String password = PASSWORD_KEY.get();

            if (email == null || password == null) {
                throw new com.health.common.exception.DomainException("Credentials missing from basic auth", Status.UNAUTHENTICATED);
            }
            TokenResponse response = adminUseCase.loginAdmin(email, password);
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
        handle(observer, () -> {
            long doctors = getLong(KEY_TOTAL_DOCTORS);
            long patients = getLong(KEY_TOTAL_PATIENTS);
            long appts = getLong(KEY_TOTAL_APPTS);

            observer.onNext(GetStatsResponse.newBuilder()
                    .setTotalDoctors(doctors)
                    .setTotalPatients(patients)
                    .setTotalAppointments(appts)
                    .setRegistrationRatePerDay(doctors / 30.0)
                    .setAppointmentVolumePerDay(appts / 30.0)
                    .build());
            observer.onCompleted();
        });
    }

    @Override
    public void getRecentEvents(GetEventsRequest request, StreamObserver<GetEventsResponse> observer) {
        handle(observer, () -> {
            int limit = request.getLimit() > 0 ? request.getLimit() : 20;
            List<String> rawEvents = redisUtil.lrange(KEY_EVENTS, 0, limit - 1);

            List<EventMessage> events = rawEvents.stream()
                    .map(json -> {
                        try {
                            Map<String, String> map = objectMapper.readValue(json, Map.class);
                            return EventMessage.newBuilder()
                                    .setTimestamp(map.getOrDefault("timestamp", ""))
                                    .setEventType(map.getOrDefault("event_type", "UNKNOWN"))
                                    .setDetails(map.getOrDefault("details", ""))
                                    .build();
                        } catch (Exception e) {
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

    private long getLong(String key) {
        String val = redisUtil.get(key, String.class);
        return val != null ? Long.parseLong(val) : 0L;
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


