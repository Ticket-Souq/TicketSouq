package org.ticketsouq.eventservice.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.ticketsouq.eventservice.dto.*;
import org.ticketsouq.eventservice.dto.FrontendMap.CreateEventWithLayoutRequest;
import org.ticketsouq.eventservice.dto.FrontendMap.EventCardResponse;
import org.ticketsouq.eventservice.dto.FrontendMap.EventLayoutResponse;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.eventservice.service.EventService;
import org.ticketsouq.eventservice.service.LockService;
import org.ticketsouq.eventservice.service.Search.SearchService;
import org.ticketsouq.eventservice.service.SeatService;
import org.ticketsouq.eventservice.service.SectionService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EventService eventService;
    @MockitoBean private SearchService eventSearchService;
    @MockitoBean private SectionService sectionService;
    @MockitoBean private SeatService seatService;
    @MockitoBean private LockService lockService;
    @MockitoBean private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    @DisplayName("Should return 201 when creating an event")
    void givenValidRequest_whenCreateEvent_thenReturn201() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateEventWithLayoutRequest request = new CreateEventWithLayoutRequest(
            "SEAT", "Event", "Desc", UUID.randomUUID(), "Concert",
            "url", Instant.now(), Instant.now().plusSeconds(7200),
            List.of(), List.of());

        mockMvc.perform(post("/api/v1/events")
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Should return 200 when fetching events")
    void givenValidUser_whenGetEvents_thenReturn200() throws Exception {
        UUID userId = UUID.randomUUID();
        Page<EventCardResponse> page = new PageImpl<>(List.of());
        when(eventService.getEvents(eq(userId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/events")
                .header("X-User-Id", userId.toString()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 200 with event ID when getting event by ID")
    void givenExistingEventId_whenGetById_thenReturn200() throws Exception {
        UUID eventId = UUID.randomUUID();
        EventLayoutResponse response = new EventLayoutResponse(
            eventId, "SEAT_BASED", "name", "desc", null, "org",
            "PUBLISHED", "cat", "url", Instant.now(), Instant.now(),
            List.of(), List.of());
        when(eventService.getById(eventId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/events/{id}", eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(eventId.toString()));
    }

    @Test
    @DisplayName("Should return 201 when creating a section")
    void givenValidSectionRequest_whenCreateSection_thenReturn201() throws Exception {
        UUID eventId = UUID.randomUUID();
        CreateSectionRequest request = new CreateSectionRequest("VIP", 100, BigDecimal.valueOf(50));
        SectionResponse response = new SectionResponse(
            UUID.randomUUID(), eventId, "VIP", 100, 100, null, BigDecimal.valueOf(50), null);
        when(sectionService.createSection(eq(eventId), any(CreateSectionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/events/{eventId}/sections", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("VIP"));
    }

    @Test
    @DisplayName("Should return 200 when updating a section")
    void givenValidUpdateRequest_whenUpdateSection_thenReturn200() throws Exception {
        UUID sectionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UpdateSectionRequest request = UpdateSectionRequest.builder()
            .price(BigDecimal.valueOf(30))
            .build();
        SectionResponse response = new SectionResponse(
            sectionId, UUID.randomUUID(), "Section", 100, 80, null, BigDecimal.valueOf(30), null);
        when(sectionService.updateSection(eq(sectionId), any(UpdateSectionRequest.class), eq(userId)))
            .thenReturn(response);

        mockMvc.perform(patch("/api/v1/events/sections/{sectionId}", sectionId)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(30));
    }

    @Test
    @DisplayName("Should return 204 when cancelling an event")
    void givenEventId_whenCancelEvent_thenReturn204() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/events/{eventId}", eventId)
                .header("X-User-Id", userId.toString()))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Should return 200 when searching events")
    void givenSearchParams_whenSearchBy_thenReturn200() throws Exception {
        Page<EventCardResponse> page = new PageImpl<>(List.of());
        when(eventSearchService.searchBy(any(EventSearchRequest.class), any(Pageable.class)))
            .thenReturn(page);

        mockMvc.perform(get("/api/v1/events/search")
                .param("title", "test"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return 200 when updating organizer seat status")
    void givenSeatIdAndStatus_whenUpdateOrganizerSeatStatus_thenReturn200() throws Exception {
        UUID seatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UpdateSeatStatusRequest request = new UpdateSeatStatusRequest(SeatStatus.BOOKED_ORGANIZER);
        SeatResponse response = new SeatResponse(seatId, UUID.randomUUID(), SeatStatus.BOOKED_ORGANIZER);
        when(seatService.updateOrganizerSeatStatus(eq(seatId), any(UpdateSeatStatusRequest.class), eq(userId)))
            .thenReturn(response);

        mockMvc.perform(patch("/api/v1/events/seats/{seatId}/status", seatId)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("BOOKED_ORGANIZER"));
    }

    @Test
    @DisplayName("Should return 200 with zone statuses when getting zone statuses")
    void givenEventId_whenGetZoneStatuses_thenReturn200() throws Exception {
        UUID eventId = UUID.randomUUID();
        List<ZoneStatusResponse> zones = List.of(
            new ZoneStatusResponse(UUID.randomUUID(), "VIP", 100, 20, 5, 75));
        when(lockService.getZoneStatuses(eventId)).thenReturn(zones);

        mockMvc.perform(get("/api/v1/events/{eventId}/zones", eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("VIP"));
    }
}
