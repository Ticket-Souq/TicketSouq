package org.ticketsouq.analyticsservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.ticketsouq.analyticsservice.model.SalesRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesRecordRepository extends JpaRepository<SalesRecord, Long> {

    Optional<SalesRecord> findByEventIdAndSaleDate(String eventId, LocalDate saleDate);

    List<SalesRecord> findByEventIdOrderBySaleDateAsc(String eventId);

    List<SalesRecord> findByEventIdAndSaleDateBetweenOrderBySaleDateAsc(String eventId, LocalDate from, LocalDate to);

    List<SalesRecord> findByOrganizationNameAndSaleDateBetweenOrderBySaleDateAsc(String orgName, LocalDate from, LocalDate to);

    List<SalesRecord> findBySaleDateBetweenOrderBySaleDateAsc(LocalDate from, LocalDate to);
}
