package com.health.common.auth;

import io.grpc.*;
import io.jsonwebtoken.Claims;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import static com.health.common.auth.GrpcAuthConstants.*;


@Slf4j
@Singleton
public class JwtAuthInterceptor implements ServerInterceptor {

    @Value("${app.auth.test-bypass-secret:}")
    private String testBypassSecret;

    public static final Context.Key<String> USER_ID_KEY  = Context.key("user-id");
    public static final Context.Key<String> ROLE_KEY     = Context.key("role");
    public static final Context.Key<String> TOKEN_TYPE_KEY = Context.key("token-type");

    private final JwtProvider jwtProvider;

    public JwtAuthInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        log.debug("JwtAuthInterceptor: Intercepting call: {}", methodName);

        if (isBypassMethod(methodName)) {
            return next.startCall(call, headers);
        }
//---------------------------------TESTHeader---------------------------------
        if(isBypassRequest(headers)){
            log.warn("Test Bypass Initiated by {} for JWT Auth",methodName);
            Context ctx = Context.current()
                    .withValue(USER_ID_KEY,
                            getOrDefault(headers,TEST_USER_ID_HEADER,"12345678-1234-1234-1234-123456789012"))
                    .withValue(ROLE_KEY,
                            getOrDefault(headers,TEST_ROLE_HEADER,"Doctor"))
                    .withValue(TOKEN_TYPE_KEY,
                            getOrDefault(headers,TEST_TOKEN_TYPE_HEADER,"ACCESS"));
            return Contexts.interceptCall(ctx,call,headers,next);
        }
//------------------------------------------------------------------------------
        String authHeader = null;
        for (Metadata.Key<String> headerKey : HEADER_TO_ROLE.keySet()) {
            String value = headers.get(headerKey);
            if (value != null) {
                authHeader = value;
                break;
            }
        }


        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Missing Bearer token for method: {}", methodName);
            return closeCall(call, headers, Status.UNAUTHENTICATED
                    .withDescription("Bearer token required"));
        }

        try {
            String token = authHeader.substring(7);

            if (!jwtProvider.isValid(token)) {
                log.warn("Invalid or expired token for method: {}", methodName);
                return closeCall(call, headers, Status.UNAUTHENTICATED
                        .withDescription("Invalid or expired token"));
            }

            Claims claims = jwtProvider.extractClaims(token);
            Context context = Context.current()
                    .withValue(USER_ID_KEY, claims.get("uid", String.class))
                    .withValue(ROLE_KEY, claims.get("role", String.class))
                    .withValue(TOKEN_TYPE_KEY, claims.get("type", String.class));

            return Contexts.interceptCall(context, call, headers, next);

        } catch (Exception e) {
            log.warn("Authentication failed for {}: {}", methodName, e.getMessage(), e);
            return closeCall(call, headers, Status.UNAUTHENTICATED
                    .withDescription("Authentication failed"));
        }
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> closeCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            Status status) {
        call.close(status, headers);
        return new ServerCall.Listener<>() {};
    }

    private boolean isBypassMethod(String methodName) {
        return BYPASS_METHODS.stream().anyMatch(methodName::endsWith);
    }

    //---------------------------TEST-HELPER--------------------------------------------------------
    private boolean isBypassRequest(Metadata headers){
        if(TEST_BYPASS_HEADER == null || testBypassSecret.isBlank()) return false;
        String token = headers.get(TEST_BYPASS_HEADER);
        return testBypassSecret.equals(token);
    }

    private String getOrDefault(Metadata headers, Metadata.Key<String> key, String defaultValue){
        String value = headers.get(key);
        return (value !=null && !value.isBlank()) ? value : defaultValue;
    }
}
