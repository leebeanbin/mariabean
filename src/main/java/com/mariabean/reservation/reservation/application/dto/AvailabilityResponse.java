package com.mariabean.reservation.reservation.application.dto;

import java.util.List;

public record AvailabilityResponse(
        String resourceItemId,
        String date,
        List<TimeSlot> slots
) {
    public record TimeSlot(
            String startTime,  // "HH:mm"
            String endTime,    // "HH:mm"
            boolean available
    ) {}
}
