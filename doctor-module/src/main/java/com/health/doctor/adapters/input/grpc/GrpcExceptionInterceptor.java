package com.health.doctor.adapters.input.grpc;

import com.health.doctor.domain.exception.DomainException;
import io.grpc.*;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GrpcExceptionInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall
                .SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                super.close(status, trailers);
            }
        };

        try {
            return new ForwardingServerCallListener
                    .SimpleForwardingServerCallListener<>(
                    next.startCall(wrappedCall, headers)) {

                @Override
                public void onMessage(ReqT message) {
                    try {
                        super.onMessage(message);
                    } catch (Exception e) {
                        handleException(e, call, headers);
                    }
                }

                @Override
                public void onHalfClose() {
                    try {
                        super.onHalfClose();
                    } catch (Exception e) {
                        handleException(e, call, headers);
                    }
                }
            };
        } catch (Exception e) {
            handleException(e, call, headers);
            return new ServerCall.Listener<>() {};
        }
    }

    private <ReqT, RespT> void handleException(
            Exception e,
            ServerCall<ReqT, RespT> call,
            Metadata headers) {

        if (e instanceof DomainException domainEx) {
            log.warn("Domain exception: {}", domainEx.getMessage());
            call.close(
                    domainEx.getGrpcStatus().withDescription(domainEx.getMessage()),
                    headers
            );
        } else if (e instanceof com.health.doctor.domain.exception.NotFoundException) {
            log.warn("Not found: {}", e.getMessage());
            call.close(
                    Status.NOT_FOUND.withDescription(e.getMessage()),
                    headers
            );
        } else if (e instanceof com.health.doctor.domain.exception.AlreadyExistsException) {
            log.warn("Already exists: {}", e.getMessage());
            call.close(
                    Status.ALREADY_EXISTS.withDescription(e.getMessage()),
                    headers
            );
        } else if (e instanceof IllegalArgumentException) {
            log.warn("Invalid argument: {}", e.getMessage());
            call.close(
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()),
                    headers
            );
        } else {
            log.error("Unexpected error in gRPC call", e);
            call.close(
                    Status.INTERNAL.withDescription("Internal server error: " + e.getMessage()),
                    headers
            );
        }
    }
}