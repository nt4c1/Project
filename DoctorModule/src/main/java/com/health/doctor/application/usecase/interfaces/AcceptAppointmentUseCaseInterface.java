package com.health.doctor.application.usecase.interfaces;

import com.health.doctor.domain.model.Appointment;

public interface AcceptAppointmentUseCaseInterface {
void execute(Appointment appointment);
}
