package com.health.common.auth;

import io.grpc.*;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static com.health.common.auth.GrpcAuthConstants.*;

@Slf4j
@Singleton
public class BasicAuthInterceptor implements ServerInterceptor {

    @Value("${app.auth.test-bypass-secret:}")
    private String testBypassSecret;

    public static final Context.Key<String> EMAIL_KEY    = Context.key("email");
    public static final Context.Key<String> PASSWORD_KEY = Context.key("password");
    public static final Context.Key<String> AUTH_ROLE_KEY = Context.key("auth-role");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String fullMethodName = call.getMethodDescriptor().getFullMethodName();//TODO Method Overhead (Extra Time)

        log.debug("BasicAuthInterceptor: Intercepting call: {} ", fullMethodName);

        if (PUBLIC_METHODS.stream().anyMatch(fullMethodName::endsWith)) {
            return next.startCall(call, headers);
        }

        //---------------------------------TestHeader---------------------------------

        if(isBypassRequest(headers)){
            log.warn("Test Bypass Initiated By {} for Basic auth ",fullMethodName);
            Context ctx = Context.current()
                    .withValue(EMAIL_KEY,
                            getOrDefault(headers,TEST_EMAIL_HEADER,"test@email.com"))
                    .withValue(PASSWORD_KEY,
                            getOrDefault(headers,TEST_PASSWORD_HEADER,"Test12345@"))
                    .withValue(AUTH_ROLE_KEY,
                            getOrDefault(headers,TEST_ROLE_HEADER,"Doctor"));
            return Contexts.interceptCall(ctx,call,headers,next);
        }
        //-----------------------------------------------------------------------


        if (LOGIN_METHODS.stream().anyMatch(fullMethodName::endsWith)) {
            return handleBasicAuth(call, headers, next);
        }


        // Pass-through to JwtAuthInterceptor
        return next.startCall(call, headers);
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> handleBasicAuth(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        String authHeader = null;
        String role = null;

        // Find the first matching role header
        for (Map.Entry<Metadata.Key<String>, String> entry : HEADER_TO_ROLE.entrySet()) {
            String value = headers.get(entry.getKey());
            if (value != null) {
                authHeader = value;
                role = entry.getValue();
                break;
            }
        }

        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            log.debug("Missing or invalid Basic Authorization header");
            return closeCall(call, headers, Status.UNAUTHENTICATED
                    .withDescription("Basic authentication required"));
        }

        try {
            String[] credentials = decodeBasicAuth(authHeader);

            if (credentials[0].isBlank() || credentials[1].isBlank()) {
                throw new IllegalArgumentException("Empty credentials");
            }

            Context context = Context.current()
                    .withValue(EMAIL_KEY, credentials[0])
                    .withValue(PASSWORD_KEY, credentials[1])
                    .withValue(AUTH_ROLE_KEY, role);


            return Contexts.interceptCall(context, call, headers, next);

        } catch (Exception e) {
            log.debug("Invalid Basic auth header: {}", e.getMessage());
            return closeCall(call, headers, Status.UNAUTHENTICATED
                    .withDescription("Invalid Basic authentication header"));
        }
    }

    private String[] decodeBasicAuth(String authHeader) {
        String base64Credentials = authHeader.substring(6);
        byte[] decoded = Base64.getDecoder().decode(base64Credentials);
        String credentials = new String(decoded, StandardCharsets.UTF_8);
        String[] values = credentials.split(":", 2);

        if (values.length != 2) {
            throw new IllegalArgumentException("Invalid Basic auth format");
        }
        return values;
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> closeCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            Status status) {
        call.close(status, headers);
        return new ServerCall.Listener<>() {};
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
