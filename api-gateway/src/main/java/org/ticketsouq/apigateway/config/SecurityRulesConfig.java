package org.ticketsouq.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.util.List;

@Configuration
public class SecurityRulesConfig {

    @Bean
    public List<SecurityRule> securityRules() {
        return List.of(
            //********************************************************************************************
            // Role-based rules are evaluated first so specific paths win over the public catch-alls
            // declared at the end (e.g. /api/v1/event/{id} would otherwise shadow /api/v1/event/management).
            //********************************************************************************************
            new SecurityRule(List.of(
                "/api/v1/ticket", // Get my tickets (also supports ?reservationId= filter)
                "/api/v1/reservation", // Get my reservations
                "/api/v1/reservation/{reservationId}" // Get a single reservation by id
            ), HttpMethod.GET, SecurityRule.Access.HAS_ROLE, List.of("CUSTOMER")),

            new SecurityRule(List.of(
                "/api/v1/payment/reservation/{reservationId}", // Get a payment + Stripe clientSecret by reservation id
                "/api/v1/payment/{paymentId}" // Get payment details by payment id
            ), HttpMethod.GET, SecurityRule.Access.HAS_ROLE, List.of("CUSTOMER")),

            new SecurityRule(List.of(
                "/api/v1/event/locks/{eventId}/seats", // Acquire seat locks for checkout
                "/api/v1/event/locks/{eventId}/zones", // Acquire a zone lock
                "/api/v1/event/locks/reserve", // Finalize a reservation from acquired locks
                "/api/v1/event/locks/confirm", // Confirm a reservation after payment
                "/api/v1/event/locks/release" // Release locks when checkout is abandoned
            ), HttpMethod.POST, SecurityRule.Access.HAS_ROLE, List.of("CUSTOMER")),

            //********************************************************************************************

            new SecurityRule(List.of(
                "/api/v1/ticket/{id}" // Get a ticket by id
            ), HttpMethod.GET, SecurityRule.Access.HAS_ANY_ROLE, List.of("ORG_Consumer", "ORG_Agent")),

            new SecurityRule(List.of(
                "/api/v1/ticket/{id}/consume" // Mark a ticket as consumed at entry
            ), HttpMethod.POST, SecurityRule.Access.HAS_ANY_ROLE, List.of("ORG_Consumer", "ORG_Agent")),

            //********************************************************************************************

            new SecurityRule(List.of(
                "/api/v1/event/management", // Org dashboard: list an organization's events for management
                "/api/v1/ticket/organizer/{eventId}", // Organizer lists tickets issued for an event
                "/api/v1/venue", // List my organization's venues
                "/api/v1/venue/{venueId}/templates" // List a venue's templates
            ), HttpMethod.GET, SecurityRule.Access.HAS_ANY_ROLE, List.of("ORG_HEAD", "ORG_Agent")),

            new SecurityRule(List.of(
                "/api/v1/event", // Create a new event with poster/banner uploads
                "/api/v1/event/{eventId}/sections", // Create a venue section for an event
                "/api/v1/ticket/reserve/organizer" // Organizer reserves a complementary ticket
            ), HttpMethod.POST, SecurityRule.Access.HAS_ANY_ROLE, List.of("ORG_HEAD", "ORG_Agent")),

            new SecurityRule(List.of(
                "/api/v1/event/sections/{sectionId}", // Update a venue section
                "/api/v1/event/sections/{sectionId}/organizer-reserve", // Reserve in section for the organizer
                "/api/v1/event/sections/{sectionId}/organizer-release", // Release an organizer-reserved in section
                "/api/v1/event/seats/{seatId}/status", // Update a seat's status
                "/api/v1/event/{eventId}/seats/template/{templateSeatId}/status" // Update a seat's status using its template seat id
            ), HttpMethod.PATCH, SecurityRule.Access.HAS_ANY_ROLE, List.of("ORG_HEAD", "ORG_Agent")),

            new SecurityRule(List.of(
                "/api/v1/ticket/{id}/status" // Update a ticket's status (also used by venue consumers in QR validation)
            ), HttpMethod.PATCH, SecurityRule.Access.HAS_ANY_ROLE, List.of("ORG_HEAD", "ORG_Agent", "ORG_Consumer")),


            new SecurityRule(List.of(
                "/api/v1/venue/{id}" // Update a venue
            ), HttpMethod.PUT, SecurityRule.Access.HAS_ANY_ROLE, List.of("ORG_HEAD", "ORG_Agent")),

            //********************************************************************************************

            new SecurityRule(List.of(
                "/api/v1/auth/org/members", // Org head lists org members with account status
                "/api/v1/analytics/overview/kpis", // Org-wide KPI dashboard
                "/api/v1/analytics/overview/sales-pace", // Sales-pace chart for an organization (or a single event)
                "/api/v1/analytics/overview/events", // Event comparison table sorted by revenue
                "/api/v1/analytics/events/{eventId}/summary", // Summary analytics for a single event
                "/api/v1/analytics/events/{eventId}/sales-timeline" // Sales timeline for a single event
            ), HttpMethod.GET, SecurityRule.Access.HAS_ROLE, List.of("ORG_HEAD")),

            new SecurityRule(List.of(
                "/api/v1/payout/my-organization/summary" // Org payout balance: earned/paid/outstanding for caller org
            ), HttpMethod.GET, SecurityRule.Access.HAS_ANY_ROLE, List.of("ORG_HEAD", "ORG_Agent")),

            new SecurityRule(List.of(
                "/api/v1/auth/org", // Org head reactivates an employee account
                "/api/v1/auth/org/generate-accounts", // Org head bulk-generates org-member accounts
                "/api/v1/venue", // Create a venue
                "/api/v1/venue/{venueId}/templates" // Create a venue template
            ), HttpMethod.POST, SecurityRule.Access.HAS_ROLE, List.of("ORG_HEAD")),

            new SecurityRule(List.of(
                "/api/v1/auth/org", // Org head deactivates an employee account
                "/api/v1/venue/{id}", // Delete a venue
                "/api/v1/venue/{venueId}/templates/{templateId}" // Delete a venue template
            ), HttpMethod.DELETE, SecurityRule.Access.HAS_ROLE, List.of("ORG_HEAD")),

            new SecurityRule(List.of(
                "/api/v1/event/{eventId}" // Cancel an event (must be org head or admin)
            ), HttpMethod.DELETE, SecurityRule.Access.HAS_ANY_ROLE, List.of("ORG_HEAD", "ADMIN")),

            //********************************************************************************************

            new SecurityRule(List.of(
                "/api/v1/audit", // List audit logs (supports madeById / action / date-range filters)
                "/api/v1/audit/{id}", // Get a single audit log by id
                "/api/v1/user/organizations" // Admin lists all organizations
            ), HttpMethod.GET, SecurityRule.Access.HAS_ROLE, List.of("ADMIN")),

            new SecurityRule(List.of(
                "/api/v1/payout/dashboard", // Admin payout dashboard: owed/paid/outstanding per org
                "/api/v1/payout/records", // Admin paginated payout records
                "/api/v1/payout/organization/{organization}/summary", // Per-org summary
                "/api/v1/payout/event/{eventId}", // Payout by event
                "/api/v1/payout/{payoutId}", // Payout by id
                "/api/v1/payout" // List payouts
            ), HttpMethod.GET, SecurityRule.Access.HAS_ROLE, List.of("ADMIN")),

            new SecurityRule(List.of(
                "/api/v1/payout/pay/organization/{organization}", // Pay all outstanding for org
                "/api/v1/payout/pay/event/{eventId}", // Pay single event
                "/api/v1/payout/{payoutId}/retry" // Retry failed payout
            ), HttpMethod.POST, SecurityRule.Access.HAS_ROLE, List.of("ADMIN")),

            new SecurityRule(List.of(
                "/api/v1/user/org/{orgHeadId}/approve", // Admin approves an organization
                "/api/v1/user/org/{orgHeadId}/ban", // Admin bans an organization
                "/api/v1/user/org/{orgHeadId}/reject" // Admin rejects an organization
                ), HttpMethod.POST, SecurityRule.Access.HAS_ROLE, List.of("ADMIN")),

            //********************************************************************************************
            // Public (PERMIT_ALL) — evaluated last so catch-alls cannot shadow role-based rules above.
            //********************************************************************************************

            new SecurityRule(List.of("/eureka/**",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/actuator/**",
                "/aggregate/*/v3/api-docs",
                "/uploads/posters/**"
            ), SecurityRule.Access.PERMIT_ALL, null),

            new SecurityRule(List.of(
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/email-varification",
                "/api/v1/auth/password-forgot"
            ), HttpMethod.POST, SecurityRule.Access.PERMIT_ALL, null),

            new SecurityRule(List.of(
                "/api/v1/auth/email-varification",
                "/api/v1/auth/password-forgot",
                "/api/v1/event/categories", // List event categories
                "/api/v1/event/search", // Search events by title/organization/category
                "/api/v1/event", // Paginated public event catalog
                "/api/v1/event/{eventId}/zones", // Get zone lock/seat statuses for an event
                "/api/v1/event/{id}", // Get full event detail
                "/api/v1/venue/{id}", // Get venue details by id
                "/api/v1/venue/{venueId}/templates/{templateId}" // Get a single venue template
                ), HttpMethod.GET, SecurityRule.Access.PERMIT_ALL, null),

            // Internal callback from user-service (org approval) via Feign without auth headers.
            // The outer /api/v1/user/org/{orgHeadId}/approve endpoint is ADMIN-gated.
            new SecurityRule(List.of(
                "/api/v1/auth/unlock-org"
            ), HttpMethod.POST, SecurityRule.Access.PERMIT_ALL, null),

            // Stripe webhook callback: payload is signature-verified by the payment-service, Stripe cannot send auth headers.
            new SecurityRule(List.of(
                "/api/v1/payment/webhook/stripe"
            ), HttpMethod.POST, SecurityRule.Access.PERMIT_ALL, null),

            //********************************************************************************************
            // AUTHENTICATED — any signed-in user (mirrors @PreAuthorize on AuthController).
            //********************************************************************************************

            new SecurityRule(List.of(
                "/api/v1/notification", // Get my notifications
                "/api/v1/notification/unread-count", // Get my unread notification count
                "/api/v1/user/profile" // Get the authenticated user's profile
            ), HttpMethod.GET, SecurityRule.Access.AUTHENTICATED, null),

            new SecurityRule(List.of(
                "/api/v1/auth/logout",
                "/api/v1/auth/logout-all"
            ), HttpMethod.POST, SecurityRule.Access.AUTHENTICATED, null),

            new SecurityRule(List.of(
                "/api/v1/auth/password" // Change my password
            ), HttpMethod.PUT, SecurityRule.Access.AUTHENTICATED, null),

            // Resolve user UUIDs to emails (used for attendee/org lookups)
            new SecurityRule(List.of("/api/v1/user/emails"), HttpMethod.POST, SecurityRule.Access.AUTHENTICATED, null),

            new SecurityRule(List.of(
                "/api/v1/notification/read-all", // Mark all notifications as read
                "/api/v1/notification/{id}/read" // Mark a single notification as read
            ), HttpMethod.PATCH, SecurityRule.Access.AUTHENTICATED, null),

            new SecurityRule(List.of(
                "/api/v1/auth" // Deactivate my account
            ), HttpMethod.DELETE, SecurityRule.Access.AUTHENTICATED, null)

        );
    }
}
