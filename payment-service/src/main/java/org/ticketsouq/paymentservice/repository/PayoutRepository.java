package org.ticketsouq.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketsouq.paymentservice.model.Payout;

import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    Optional<Payout> findByEventId(UUID eventId);

    boolean existsByEventId(UUID eventId);

    java.util.List<Payout> findByOrganizerId(UUID organizerId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.organization FROM Payout p WHERE p.organization IS NOT NULL")
    java.util.List<String> findDistinctOrganizations();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.organization FROM Payout p WHERE p.organization IS NOT NULL AND LOWER(p.organization) LIKE LOWER(CONCAT('%', :search, '%'))")
    java.util.List<String> findDistinctOrganizationsBySearch(@org.springframework.data.repository.query.Param("search") String search);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p.eventId FROM Payout p WHERE p.organization = :org AND p.eventId IS NOT NULL")
    java.util.List<UUID> findDistinctEventIdsByOrganization(@org.springframework.data.repository.query.Param("org") String org);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(p.netAmount), 0) FROM Payout p WHERE p.organization = :org AND p.status = 'COMPLETED'")
    java.math.BigDecimal sumPaidByOrganization(@org.springframework.data.repository.query.Param("org") String org);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(p.netAmount), 0) FROM Payout p WHERE p.eventId = :eventId AND p.status = 'COMPLETED'")
    java.math.BigDecimal sumPaidByEventId(@org.springframework.data.repository.query.Param("eventId") UUID eventId);

    org.springframework.data.domain.Page<Payout> findByOrganization(String organization, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Payout> findByOrganizationAndStatus(String organization, String status, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Payout> findByStatus(String status, org.springframework.data.domain.Pageable pageable);

    java.util.List<Payout> findByOrganization(String organization);
}
