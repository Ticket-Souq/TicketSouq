package org.ticketsouq.analyticsservice.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock
    private EventAnalyticsRepository eventAnalyticsRepository;

    @Mock
    private SalesRecordRepository salesRecordRepository;

    @InjectMocks
    private AnalyticsServiceImpl service;

    private final String orgId = "org-1";

    // ──── getOverviewKpis ────

    @Test
    void getOverviewKpis_shouldReturnOrgScopedKpis() {
        when(eventAnalyticsRepository.sumTotalRevenueByOrg(orgId)).thenReturn(5000.0);
        when(eventAnalyticsRepository.sumTotalTicketsSoldByOrg(orgId)).thenReturn(100);
        when(eventAnalyticsRepository.sumTotalCapacityByOrg(orgId)).thenReturn(200);

        OverviewKpiResponse response = service.getOverviewKpis(orgId, "30d");

        assertEquals(5000.0, response.revenue().value());
        assertEquals("USD", response.revenue().currency());
        assertEquals(100, response.ticketsSold().value());
        assertEquals(200, response.ticketsSold().capacity());
        assertEquals(50.0, response.avgTicketPrice().value());
        assertNull(response.checkInRate().valuePct());
        assertNull(response.checkInRate().noShowPct());
    }

    @Test
    void getOverviewKpis_whenNoTicketsSold_avgPriceShouldBeZero() {
        when(eventAnalyticsRepository.sumTotalRevenueByOrg(orgId)).thenReturn(0.0);
        when(eventAnalyticsRepository.sumTotalTicketsSoldByOrg(orgId)).thenReturn(0);
        when(eventAnalyticsRepository.sumTotalCapacityByOrg(orgId)).thenReturn(0);

        OverviewKpiResponse response = service.getOverviewKpis(orgId, "30d");

        assertEquals(0.0, response.avgTicketPrice().value());
    }

    // ──── getSalesPace ────

    @Test
    void getSalesPace_withEventId_shouldQueryByEventAndDate() {
        when(salesRecordRepository.findByEventIdAndSaleDateBetweenOrderBySaleDateAsc(
                anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        service.getSalesPace(orgId, "30d", Optional.of("evt-1"));

        verify(salesRecordRepository).findByEventIdAndSaleDateBetweenOrderBySaleDateAsc(
                eq("evt-1"), any(LocalDate.class), any(LocalDate.class));
        verify(salesRecordRepository, never()).findByOrganizationIdAndSaleDateBetweenOrderBySaleDateAsc(
                anyString(), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void getSalesPace_withoutEventId_shouldQueryByOrgAndDate() {
        when(salesRecordRepository.findByOrganizationIdAndSaleDateBetweenOrderBySaleDateAsc(
                anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        service.getSalesPace(orgId, "30d", Optional.empty());

        verify(salesRecordRepository).findByOrganizationIdAndSaleDateBetweenOrderBySaleDateAsc(
                eq(orgId), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void getSalesPace_shouldMapTicketsSold_defaultZeroWhenNull() {
        SalesRecord record = SalesRecord.builder()
                .saleDate(LocalDate.of(2026, 7, 21))
                .ticketsSold(null)
                .revenue(BigDecimal.valueOf(100))
                .build();
        when(salesRecordRepository.findByOrganizationIdAndSaleDateBetweenOrderBySaleDateAsc(
                anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(record));

        SalesPaceResponse response = service.getSalesPace(orgId, "30d", Optional.empty());

        assertEquals(1, response.series().size());
        assertEquals(0, response.series().get(0).ticketsCumulative());
    }

    @Test
    void getSalesPace_shouldMapTicketsSoldCorrectly() {
        SalesRecord record = SalesRecord.builder()
                .saleDate(LocalDate.of(2026, 7, 21))
                .ticketsSold(15)
                .revenue(BigDecimal.valueOf(300))
                .build();
        when(salesRecordRepository.findByOrganizationIdAndSaleDateBetweenOrderBySaleDateAsc(
                anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(record));

        SalesPaceResponse response = service.getSalesPace(orgId, "30d", Optional.empty());

        assertEquals("2026-07-21", response.series().get(0).date());
        assertEquals(15, response.series().get(0).ticketsCumulative());
    }

    // ──── getEventComparison ────

    @Test
    void getEventComparison_shouldSortByRevenueDescAsDefault() {
        when(eventAnalyticsRepository.findByOrganizationId(eq(orgId), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getEventComparison(orgId, "30d", "unknown-sort", 1, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.captor();
        verify(eventAnalyticsRepository).findByOrganizationId(eq(orgId), captor.capture());
        Sort.Order order = captor.getValue().getSort().iterator().next();
        assertEquals("totalRevenue", order.getProperty());
        assertTrue(order.isDescending());
    }

    @Test
    void getEventComparison_shouldSortByTicketsDesc() {
        when(eventAnalyticsRepository.findByOrganizationId(eq(orgId), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getEventComparison(orgId, "30d", "tickets", 1, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.captor();
        verify(eventAnalyticsRepository).findByOrganizationId(eq(orgId), captor.capture());
        assertEquals("totalTicketsSold", captor.getValue().getSort().iterator().next().getProperty());
    }

    @Test
    void getEventComparison_shouldSortByNameAsc() {
        when(eventAnalyticsRepository.findByOrganizationId(eq(orgId), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getEventComparison(orgId, "30d", "name", 1, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.captor();
        verify(eventAnalyticsRepository).findByOrganizationId(eq(orgId), captor.capture());
        Sort.Order order = captor.getValue().getSort().iterator().next();
        assertEquals("title", order.getProperty());
        assertTrue(order.isAscending());
    }

    @Test
    void getEventComparison_shouldUseCorrectPagination() {
        when(eventAnalyticsRepository.findByOrganizationId(eq(orgId), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getEventComparison(orgId, "30d", "revenue", 3, 15);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.captor();
        verify(eventAnalyticsRepository).findByOrganizationId(eq(orgId), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(15, captor.getValue().getPageSize());
    }

    @Test
    void getEventComparison_shouldMapRowFromEntity() {
        EventAnalytics entity = EventAnalytics.builder()
                .eventId("evt-1")
                .title("Summer Festival")
                .startDateTime(Instant.parse("2026-08-15T16:00:00Z"))
                .totalTicketsSold(50)
                .capacity(100)
                .totalRevenue(BigDecimal.valueOf(2500))
                .build();
        when(eventAnalyticsRepository.findByOrganizationId(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        EventComparisonResponse response = service.getEventComparison(orgId, "30d", "revenue", 1, 20);

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
                .organizationId(orgId)
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