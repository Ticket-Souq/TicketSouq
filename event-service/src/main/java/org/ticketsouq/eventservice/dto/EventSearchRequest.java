package org.ticketsouq.eventservice.dto;

public record EventSearchRequest(
    String title,
    String organization,
    String category
) {
}
