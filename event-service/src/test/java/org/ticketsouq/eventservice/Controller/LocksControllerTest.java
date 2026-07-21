package org.ticketsouq.eventservice.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.ticketsouq.eventservice.service.LockService;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockSeatsResponse;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneRequest;
import org.ticketsouq.sharedmodule.EventService.dto.LockZoneResponse;
import org.ticketsouq.sharedmodule.ReservationService.dto.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LocksController.class)
class LocksControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private JpaMetamodelMappingContext jpaMappingContext;
    @MockitoBean private LockService lockService;

    @Test
    @DisplayName("Should return 200 with LOCKED status when locking seats")
    void givenSeatLockRequest_whenLockSeats_thenReturn200() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        LockSeatsRequest request = new LockSeatsRequest(List.of(seatId));
        LockSeatsResponse response = new LockSeatsResponse(UUID.randomUUID(), "LOCKED", LocalDateTime.now().plusMinutes(10), request.seatIds());
        when(lockService.acquireSeatLocks(eq(eventId), any(LockSeatsRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/events/locks/{eventId}/seats", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    @DisplayName("Should return 200 with LOCKED status when locking a zone")
    void givenZoneLockRequest_whenLockZone_thenReturn200() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        LockZoneRequest request = new LockZoneRequest(zoneId, 5);
        LockZoneResponse response = new LockZoneResponse(UUID.randomUUID(), "LOCKED", LocalDateTime.now().plusMinutes(10), request.zoneId(), request.quantity());
        when(lockService.acquireZoneLock(eq(eventId), any(LockZoneRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/events/locks/{eventId}/zones", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("LOCKED"))
            .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    @DisplayName("Should return 200 with CONFIRMED status when confirming a reservation")
    void givenReservationId_whenConfirm_thenReturn200() throws Exception {
        ConfirmRequest request = new ConfirmRequest("res-1");
        when(lockService.confirm("res-1")).thenReturn(ConfirmResponse.CONFIRMED);

        mockMvc.perform(post("/api/v1/events/locks/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Should return 200 with RELEASED status when releasing a reservation")
    void givenReservationId_whenRelease_thenReturn200() throws Exception {
        ReleaseRequest request = new ReleaseRequest("res-1");
        when(lockService.release("res-1")).thenReturn(ReleaseResponse.RELEASED);

        mockMvc.perform(post("/api/v1/events/locks/release")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RELEASED"));
    }
}
