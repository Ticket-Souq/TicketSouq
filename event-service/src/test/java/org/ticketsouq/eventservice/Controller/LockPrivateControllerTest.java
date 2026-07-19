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
import org.ticketsouq.eventservice.dto.*;
import org.ticketsouq.eventservice.service.LockService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LockPrivateController.class)
class LockPrivateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private JpaMetamodelMappingContext jpaMappingContext;
    @MockitoBean private LockService lockService;

    @Test
    @DisplayName("Should return 200 with LOCKED status when locking seats")
    void givenSeatLockRequest_whenLockSeats_thenReturn200() throws Exception {
        UUID eventId = UUID.randomUUID();
        LockSeatsRequest request = new LockSeatsRequest("res-1", List.of(UUID.randomUUID()));
        LockSeatsResponse response = new LockSeatsResponse("LOCKED", LocalDateTime.now().plusMinutes(10), request.seatIds());
        when(lockService.acquireSeatLocks(eq(eventId), any(LockSeatsRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/private/events/{eventId}/locks/seats", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    @DisplayName("Should return 200 with LOCKED status when locking a zone")
    void givenZoneLockRequest_whenLockZone_thenReturn200() throws Exception {
        UUID eventId = UUID.randomUUID();
        LockZoneRequest request = new LockZoneRequest("res-1", UUID.randomUUID(), 5);
        LockZoneResponse response = new LockZoneResponse("LOCKED", LocalDateTime.now().plusMinutes(10), request.zoneId(), 5);
        when(lockService.acquireZoneLock(eq(eventId), any(LockZoneRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/private/events/{eventId}/locks/zones", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("LOCKED"))
            .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    @DisplayName("Should return 200 with CONFIRMED status when confirming a reservation")
    void givenReservationId_whenConfirm_thenReturn200() throws Exception {
        UUID eventId = UUID.randomUUID();
        ConfirmRequest request = new ConfirmRequest("res-1");
        when(lockService.confirm("res-1")).thenReturn(ConfirmResponse.CONFIRMED);

        mockMvc.perform(post("/api/v1/private/events/{eventId}/confirm", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Should return 200 with RELEASED status when releasing a reservation")
    void givenReservationId_whenRelease_thenReturn200() throws Exception {
        UUID eventId = UUID.randomUUID();
        ReleaseRequest request = new ReleaseRequest("res-1");
        when(lockService.release("res-1")).thenReturn(ReleaseResponse.RELEASED);

        mockMvc.perform(post("/api/v1/private/events/{eventId}/release", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RELEASED"));
    }
}
