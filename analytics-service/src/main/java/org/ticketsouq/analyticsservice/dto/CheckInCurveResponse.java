package org.ticketsouq.analyticsservice.dto;

import java.util.List;

public record CheckInCurveResponse(
    String eventDate,
    List<HourlyCheckIn> series,
    String peakHour
) {
    public record HourlyCheckIn(String hour, int checkIns) {}
}
