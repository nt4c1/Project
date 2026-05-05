package com.health.app;

import com.health.grpc.notification.AppointmentAcceptedEvent;
import com.health.grpc.notification.AppointmentBookedEvent;
import com.health.grpc.notification.AppointmentCancelledEvent;
import com.health.grpc.notification.AppointmentPostponedEvent;
import io.micronaut.runtime.Micronaut;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(AppointmentBookedEvent.class)
@SerdeImport(AppointmentAcceptedEvent.class)
@SerdeImport(AppointmentPostponedEvent.class)
@SerdeImport(AppointmentCancelledEvent.class)
@SerdeImport(com.health.grpc.common.AppointmentMessage.class)
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}