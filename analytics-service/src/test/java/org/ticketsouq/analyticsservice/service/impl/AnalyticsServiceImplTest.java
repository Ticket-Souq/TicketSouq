package org.ticketsouq.analyticsservice.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.ticketsouq.analyticsservice.Client.UserServiceClient;
import org.ticketsouq.analyticsservice.dto.*;
import org.ticketsouq.analyticsservice.model.EventAnalytics;
import org.ticketsouq.analyticsservice.model.SalesRecord;
import org.ticketsouq.analyticsservice.repository.EventAnalyticsRepository;
import org.ticketsouq.analyticsservice.repository.SalesRecordRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock
    private EventAnalyticsRepository eventAnalyticsRepository;

    @Mock
    private SalesRecordRepository salesRecordRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private AnalyticsServiceImpl service;

    private final String userId = "11111111-1111-1111-1111-111111111111";
    private final String orgName = "org-1";

    private void stubOrgName() {
        when(userServiceClient.getOrganizationNameByUserId(UUID.fromString(userId))).thenReturn(orgName);
    }

    // ──── getOverviewKpis ────

    @Test
    void getOverviewKpis_shouldReturnOrgScopedKpis() {
        stubOrgName();
        when(eventAnalyticsRepository.sumTotalRevenueByOrgName(orgName)).thenReturn(5000.0);
        when(eventAnalyticsRepository.sumTotalTicketsSoldByOrgName(orgName)).thenReturn(100);
        when(eventAnalyticsRepository.sumTotalCapacityByOrgName(orgName)).thenReturn(200);

        OverviewKpiResponse response = service.getOverviewKpis(userId);

        assertEquals(5000.0, response.revenue().value());
        assertEquals("EGP", response.revenue().currency());
        assertEquals(100, response.ticketsSold().value());
        assertEquals(200, response.ticketsSold().capacity());
        assertEquals(50.0, response.avgTicketPrice().value());
        assertNull(response.checkInRate().valuePct());
        assertNull(response.checkInRate().noShowPct());
    }

    @Test
    void getOverviewKpis_whenNoTicketsSold_avgPriceShouldBeZero() {
        stubOrgName();
        when(eventAnalyticsRepository.sumTotalRevenueByOrgName(orgName)).thenReturn(0.0);
        when(eventAnalyticsRepository.sumTotalTicketsSoldByOrgName(orgName)).thenReturn(0);
        when(eventAnalyticsRepository.sumTotalCapacityByOrgName(orgName)).thenReturn(0);

        OverviewKpiResponse response = service.getOverviewKpis(userId);

        assertEquals(0.0, response.avgTicketPrice().value());
    }

    // ──── getSalesPace ────

    @Test
    void getSalesPace_withEventId_shouldQueryByEvent() {
        when(salesRecordRepository.findByEventIdOrderBySaleDateAsc("evt-1"))
                .thenReturn(List.of());

        service.getSalesPace(userId, Optional.of("evt-1"));

        verify(salesRecordRepository).findByEventIdOrderBySaleDateAsc("evt-1");
        verify(salesRecordRepository, never()).findByOrganizationNameOrderBySaleDateAsc(anyString());
    }

    @Test
    void getSalesPace_withoutEventId_shouldQueryByOrg() {
        stubOrgName();
        when(salesRecordRepository.findByOrganizationNameOrderBySaleDateAsc(orgName))
                .thenReturn(List.of());

        service.getSalesPace(userId, Optional.empty());

        verify(salesRecordRepository).findByOrganizationNameOrderBySaleDateAsc(orgName);
    }

    @Test
    void getSalesPace_shouldMapTicketsSold_defaultZeroWhenNull() {
        stubOrgName();
        SalesRecord record = SalesRecord.builder()
                .saleDate(LocalDate.of(2026, 7, 21))
                .ticketsSold(null)
                .revenue(BigDecimal.valueOf(100))
                .build();
        when(salesRecordRepository.findByOrganizationNameOrderBySaleDateAsc(orgName))
                .thenReturn(List.of(record));

        SalesPaceResponse response = service.getSalesPace(userId, Optional.empty());

        assertEquals(1, response.series().size());
        assertEquals(0, response.series().get(0).ticketsCumulative());
    }

    @Test
    void getSalesPace_shouldMapTicketsSoldCorrectly() {
        stubOrgName();
        SalesRecord record = SalesRecord.builder()
                .saleDate(LocalDate.of(2026, 7, 21))
                .ticketsSold(15)
                .revenue(BigDecimal.valueOf(300))
                .build();
        when(salesRecordRepository.findByOrganizationNameOrderBySaleDateAsc(orgName))
                .thenReturn(List.of(record));

        SalesPaceResponse response = service.getSalesPace(userId, Optional.empty());

        assertEquals("2026-07-21", response.series().get(0).date());
        assertEquals(15, response.series().get(0).ticketsCumulative());
    }

    // ──── getEventComparison ────

    @Test
    void getEventComparison_shouldForwardRevenueDescSort() {
        stubOrgName();
        when(eventAnalyticsRepository.findByOrganizationName(eq(orgName), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getEventComparison(userId, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "totalRevenue")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.captor();
        verify(eventAnalyticsRepository).findByOrganizationName(eq(orgName), captor.capture());
        Sort.Order order = captor.getValue().getSort().iterator().next();
        assertEquals("totalRevenue", order.getProperty());
        assertTrue(order.isDescending());
    }

    @Test
    void getEventComparison_shouldForwardTicketsDescSort() {
        stubOrgName();
        when(eventAnalyticsRepository.findByOrganizationName(eq(orgName), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getEventComparison(userId, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "totalTicketsSold")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.captor();
        verify(eventAnalyticsRepository).findByOrganizationName(eq(orgName), captor.capture());
        assertEquals("totalTicketsSold", captor.getValue().getSort().iterator().next().getProperty());
    }

    @Test
    void getEventComparison_shouldForwardNameAscSort() {
        stubOrgName();
        when(eventAnalyticsRepository.findByOrganizationName(eq(orgName), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getEventComparison(userId, PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "title")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.captor();
        verify(eventAnalyticsRepository).findByOrganizationName(eq(orgName), captor.capture());
        Sort.Order order = captor.getValue().getSort().iterator().next();
        assertEquals("title", order.getProperty());
        assertTrue(order.isAscending());
    }

    @Test
    void getEventComparison_shouldUseProvidedPagination() {
        stubOrgName();
        when(eventAnalyticsRepository.findByOrganizationName(eq(orgName), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getEventComparison(userId, PageRequest.of(2, 15));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.captor();
        verify(eventAnalyticsRepository).findByOrganizationName(eq(orgName), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(15, captor.getValue().getPageSize());
    }

    @Test
    void getEventComparison_shouldMapRowFromEntity() {
        stubOrgName();
        EventAnalytics entity = EventAnalytics.builder()
                .eventId("evt-1")
                .title("Summer Festival")
                .startDateTime(Instant.parse("2026-08-15T16:00:00Z"))
                .totalTicketsSold(50)
                .capacity(100)
                .totalRevenue(BigDecimal.valueOf(2500))
                .build();
        when(eventAnalyticsRepository.findByOrganizationName(eq(orgName), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        EventComparisonResponse response = service.getEventComparison(userId, PageRequest.of(0, 20));

        assertEquals(1, response.events().size());
        EventComparisonResponse.EventRow row = response.events().get(0);
        assertEquals("evt-1", row.eventId());
        assertEquals("Summer Festival", row.name());
        assertEquals(50, row.sold());
        assertEquals(100, row.capacity());
        assertEquals(2500.0, row.revenue());
        assertNull(row.noShowPct());
    }

    // ──── getEventSummary ────

    @Test
    void getEventSummary_whenNotFound_shouldReturnUnknown() {
        when(eventAnalyticsRepository.findById("missing-id")).thenReturn(Optional.empty());

        EventSummaryResponse response = service.getEventSummary("missing-id");

        assertEquals("missing-id", response.eventId());
        assertEquals("Unknown", response.name());
        assertNull(response.kpis());
    }

    @Test
    void getEventSummary_whenFound_shouldReturnEventData() {
        EventAnalytics entity = EventAnalytics.builder()
                .eventId("evt-1")
                .title("Summer Festival")
                .organizationName(orgName)
                .startDateTime(Instant.parse("2026-08-15T16:00:00Z"))
                .totalTicketsSold(50)
                .capacity(100)
                .totalRevenue(BigDecimal.valueOf(2500))
                .build();
        when(eventAnalyticsRepository.findById("evt-1")).thenReturn(Optional.of(entity));

        EventSummaryResponse response = service.getEventSummary("evt-1");

        assertEquals("Summer Festival", response.name());
        assertEquals(50, response.kpis().sold().value());
        assertEquals(2500.0, response.kpis().revenue().value());
        assertEquals(100, response.capacity());
        assertNull(response.kpis().checkInRate().valuePct());
        assertNull(response.kpis().refundRate().valuePct());
    }

    // ──── getEventSalesTimeline ────

    @Test
    void getEventSalesTimeline_shouldReturnDailySeries() {
        SalesRecord record = SalesRecord.builder()
                .saleDate(LocalDate.of(2026, 7, 21))
                .ticketsSold(10)
                .revenue(BigDecimal.valueOf(200))
                .build();
        when(salesRecordRepository.findByEventIdOrderBySaleDateAsc("evt-1"))
                .thenReturn(List.of(record));

        EventSalesTimelineResponse response = service.getEventSalesTimeline("evt-1", "day");

        assertEquals("day", response.granularity());
        assertEquals(1, response.series().size());
        assertEquals("2026-07-21", response.series().get(0).period());
        assertEquals(10, response.series().get(0).ticketsCumulative());
    }

    @Test
    void getEventSalesTimeline_whenTicketsSoldNull_shouldDefaultToZero() {
        SalesRecord record = SalesRecord.builder()
                .saleDate(LocalDate.of(2026, 7, 21))
                .ticketsSold(null)
                .revenue(BigDecimal.valueOf(200))
                .build();
        when(salesRecordRepository.findByEventIdOrderBySaleDateAsc("evt-1"))
                .thenReturn(List.of(record));

        EventSalesTimelineResponse response = service.getEventSalesTimeline("evt-1", "day");

        assertEquals(0, response.series().get(0).ticketsCumulative());
    }
}