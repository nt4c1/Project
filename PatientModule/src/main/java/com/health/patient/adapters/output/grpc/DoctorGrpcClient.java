package com.health.patient.adapters.output.grpc;

import com.health.grpc.doctor.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Singleton
public class DoctorGrpcClient {

    private final DoctorGrpcServiceGrpc.DoctorGrpcServiceBlockingStub stub;

    public DoctorGrpcClient() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 50051)
                .usePlaintext()
                .build();
        this.stub = DoctorGrpcServiceGrpc.newBlockingStub(channel);
    }

    // ── Doctor discovery ──────────────────────────────────────────────────────

    public List<DoctorMessage> getNearbyDoctors(String locationText) {
        try {
            return stub.getNearbyDoctors(NearbyDoctorsRequest.newBuilder()
                    .setLocationText(locationText).build()).getDoctorsList();
        } catch (Exception e) {
            log.error("getNearbyDoctors error", e);
            return List.of();
        }
    }

    public List<DoctorMessage> getDoctorsByLocation(String geohashPrefix) {
        try {
            return stub.getDoctorsByLocation(ByLocationRequest.newBuilder()
                    .setGeohashPrefix(geohashPrefix).build()).getDoctorsList();
        } catch (Exception e) {
            log.error("getDoctorsByLocation error", e);
            return List.of();
        }
    }

    public GetScheduleResponse getDoctorSchedule(String doctorId) {
        try {
            return stub.getDoctorSchedule(GetScheduleRequest.newBuilder()
                    .setDoctorId(doctorId).build());
        } catch (Exception e) {
            log.error("getDoctorSchedule error", e);
            return null;
        }
    }

    // ── Appointments ──────────────────────────────────────────────────────────

    public CreateAppointmentResponse createAppointment(String doctorId, String patientId,
                                                       String date, String time,
                                                       String reasonForVisit) {
        try {
            return stub.createAppointment(
                    CreateAppointmentRequest.newBuilder()
                            .setDoctorId(doctorId)
                            .setPatientId(patientId)
                            .setDate(date)
                            .setTime(time)
                            .setReasonForVisit(reasonForVisit != null ? reasonForVisit : "")
                            .build()
            );
        } catch (Exception e) {
            log.error("createAppointment error", e);
            return CreateAppointmentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error connecting to doctor service: " + e.getMessage())
                    .build();
        }
    }


    public CancelAppointmentResponse cancelAppointment(String appointmentId, String patientId,
                                                       String doctorId, String date, String time,
                                                       String cancellationReason) {
        try {
            return stub.cancelAppointment(
                    CancelAppointmentRequest.newBuilder()
                            .setAppointmentId(appointmentId)
                            .setPatientId(patientId)
                            .setDoctorId(doctorId)
                            .setDate(date)
                            .setTime(time)
                            .setCancellationReason(cancellationReason != null ? cancellationReason : "")
                            .build()
            );
        } catch (Exception e) {
            log.error("cancelAppointment error", e);
            return CancelAppointmentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error connecting to doctor service: " + e.getMessage())
                    .build();
        }
    }

    public List<AppointmentMessage> getMyAppointments(String patientId, String date) {
        try {
            return stub.getMyAppointments(GetMyAppointmentsRequest.newBuilder()
                    .setPatientId(patientId).setDate(date).build()).getAppointmentsList();
        } catch (Exception e) {
            log.error("getMyAppointments error", e);
            return List.of();
        }
    }
}