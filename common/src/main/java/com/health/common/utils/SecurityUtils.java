package com.health.common.utils;

import com.health.common.auth.JwtAuthInterceptor;
import com.health.common.exception.UnauthorizedException;
import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID getCurrentUserId() {
        String id = JwtAuthInterceptor.USER_ID_KEY.get();
        return id != null ? UUID.fromString(id) : null;
    }

    public static String getCurrentUserRole() {
        return JwtAuthInterceptor.ROLE_KEY.get();
    }

    public static boolean isDoctor() {
        return "Doctor".equalsIgnoreCase(getCurrentUserRole());
    }

    public static boolean isPatient() {
        return "Patient".equalsIgnoreCase(getCurrentUserRole());
    }

    public static boolean isClinic() {
        return "Clinic".equalsIgnoreCase(getCurrentUserRole());
    }

    public static boolean isAdmin() {
        return "Admin".equalsIgnoreCase(getCurrentUserRole());
    }

    public static void validateOwnership(UUID ownerId) {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(ownerId)) {
            throw new UnauthorizedException("Access Denied: You do not have permission to access this resource.");
        }
    }
}
