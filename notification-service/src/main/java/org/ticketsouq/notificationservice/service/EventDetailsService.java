package org.ticketsouq.notificationservice.service;

import org.ticketsouq.notificationservice.dto.EventDetailsResponse;

import java.util.UUID;

public interface EventDetailsService {

    EventDetailsResponse getEvent(UUID eventId);

}
