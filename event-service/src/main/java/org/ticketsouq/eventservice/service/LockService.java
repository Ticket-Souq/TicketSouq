package org.ticketsouq.eventservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.sharedmodule.EventService.dto.ReservationRequest;
import org.ticketsouq.eventservice.dto.ZoneStatusResponse;
import org.ticketsouq.eventservice.model.*;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.eventservice.repository.*;
import org.ticketsouq.sharedmodule.EventService.dto.*;
import org.ticketsouq.sharedmodule.EventService.events.BeginReservationEvent;
import org.ticketsouq.sharedmodule.EventService.exception.*;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;
import org.ticketsouq.sharedmodule.ReservationService.dto.ConfirmResponse;
import org.ticketsouq.sharedmodule.ReservationService.dto.ReleaseResponse;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LockService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final SectionRepository sectionRepository;
    private final SeatLockRepository seatLockRepository;
    private final ZoneLockRepository zoneLockRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.lock.ttl:10}")
    private int lockTtlMinutes;

    @Transactional
    public LockSeatsResponse acquireSeatLocks(UUID eventId, LockSeatsRequest request) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException("Only published events can be locked.");
        }
        if (event.getBookingModel() != BookingModel.SEAT) {
            throw new InvalidEventTypeException(BookingModel.SEAT.name());
        }

        List<Seat> seats = seatRepository.findByIdInAndEventIdWithLock(request.seatIds(), eventId);

        if (seats.size() != request.seatIds().size()) {
            Set<UUID> foundIds = seats.stream().map(Seat::getId).collect(Collectors.toSet());
            List<UUID> missing = request.seatIds().stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
            throw new SeatNotInEventException(missing);
        }

        List<UUID> bookedSeats = seats.stream()
            .filter(s -> s.getStatus() == SeatStatus.BOOKED)
            .map(Seat::getId)
            .toList();
        if (!bookedSeats.isEmpty()) {
            throw new SeatAlreadyBookedException(bookedSeats);
        }

        List<SeatLock> activeLocks = seatLockRepository.findBySeatIdInAndExpiresAtAfter(
            request.seatIds(), LocalDateTime.now());
        if (!activeLocks.isEmpty()) {
            List<UUID> conflicting = activeLocks.stream()
                .map(SeatLock::getSeatId)
                .toList();
            throw new SeatAlreadyLockedException(conflicting);
        }
        UUID reservationId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(lockTtlMinutes);
        List<SeatLock> locks = request.seatIds().stream()
            .map(seatId -> SeatLock.builder()
                .seatId(seatId)
                .reservationId(String.valueOf(reservationId))
                .expiresAt(expiresAt)
                .build())
            .toList();
        seatLockRepository.saveAll(locks);

        return new LockSeatsResponse(reservationId, "LOCKED", expiresAt, request.seatIds());
    }

    @Transactional
    public LockZoneResponse acquireZoneLock(UUID eventId, LockZoneRequest request) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException("Only published events can be locked.");
        }
        if (event.getBookingModel() != BookingModel.ZONE) {
            throw new InvalidEventTypeException(BookingModel.ZONE.name());
        }

        Section section = sectionRepository.findByIdAndEventIdWithLock(request.zoneId(), eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Section", request.zoneId()));

        int activeSum = zoneLockRepository.sumActiveQuantityByZoneId(request.zoneId(), LocalDateTime.now());

        int available = section.getRemainingCapacity() - activeSum;

        if (available < request.quantity()) {
            throw new ZoneCapacityExceededException(available);
        }
        UUID reservationId = UUID.randomUUID();
        ZoneLock zoneLock = ZoneLock.builder()
            .zoneId(request.zoneId())
            .reservationId(String.valueOf(reservationId))
            .quantity(request.quantity())
            .expiresAt(LocalDateTime.now().plusMinutes(lockTtlMinutes))
            .build();
        zoneLockRepository.save(zoneLock);

        return new LockZoneResponse(reservationId, "LOCKED", zoneLock.getExpiresAt(), request.zoneId(), request.quantity());
    }

    @Transactional
    public ConfirmResponse confirm(String reservationId) {
        List<SeatLock> seatLocks = seatLockRepository.findByReservationIdWithLock(reservationId);
        Optional<ZoneLock> zoneLockOpt = zoneLockRepository.findByReservationIdWithLock(reservationId);

        if (seatLocks.isEmpty() && zoneLockOpt.isEmpty()) {
            return ConfirmResponse.CONFIRMED;
        }

        if (!seatLocks.isEmpty()) {
            confirmSeats(seatLocks, reservationId);
        } else {
            confirmZone(zoneLockOpt.get(), reservationId);
        }

        return ConfirmResponse.CONFIRMED;
    }

    private void confirmSeats(List<SeatLock> seatLocks, String reservationId) {
        LocalDateTime now = LocalDateTime.now();
        boolean expired = seatLocks.stream().anyMatch(sl -> sl.getExpiresAt().isBefore(now));
        if (expired) {
            seatLockRepository.deleteByReservationId(reservationId);
            throw new LockExpiredException(reservationId);
        }

        List<UUID> seatIds = seatLocks.stream()
            .map(SeatLock::getSeatId)
            .sorted()
            .toList();

            List<Seat> seats = seatRepository.findByIdsWithSection(seatIds);

        List<UUID> bookedSeats = seats.stream()
            .filter(s -> s.getStatus() == SeatStatus.BOOKED)
            .map(Seat::getId)
            .toList();
        if (!bookedSeats.isEmpty()) {
            throw new SeatAlreadyBookedException(bookedSeats);
        }

        seats.forEach(seat -> seat.setStatus(SeatStatus.BOOKED));
        seatRepository.saveAll(seats);
        seatLockRepository.deleteByReservationId(reservationId);
    }

    private void confirmZone(ZoneLock zoneLock, String reservationId) {
        if (zoneLock.getExpiresAt().isBefore(LocalDateTime.now())) {
            zoneLockRepository.deleteByReservationId(reservationId);
            throw new LockExpiredException(reservationId);
        }

        Section section = sectionRepository.findByIdWithLock(zoneLock.getZoneId())
            .orElseThrow(() -> new ResourceNotFoundException("Section", zoneLock.getZoneId()));

        section.setRemainingCapacity(section.getRemainingCapacity() - zoneLock.getQuantity());
        zoneLockRepository.deleteByReservationId(reservationId);
    }

    @Transactional
    public ReleaseResponse release(String reservationId) {
        seatLockRepository.deleteByReservationId(reservationId);
        zoneLockRepository.deleteByReservationId(reservationId);
        return ReleaseResponse.RELEASED;
    }

    @Transactional(readOnly = true)
    public List<ZoneStatusResponse> getZoneStatuses(UUID eventId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));

        if (event.getBookingModel() != BookingModel.ZONE) {
            throw new InvalidEventTypeException(BookingModel.ZONE.name());
        }

        List<Section> sections = event.getSections();
        if (sections == null) return List.of();

        LocalDateTime now = LocalDateTime.now();
        return sections.stream()
            .map(section -> {
                int activeSum = zoneLockRepository.sumActiveQuantityByZoneId(section.getId(), now);
                int booked = section.getCapacity() - section.getRemainingCapacity();
                return new ZoneStatusResponse(
                    section.getId(),
                    section.getName(),
                    section.getCapacity(),
                    booked,
                    activeSum,
                    section.getRemainingCapacity() - activeSum
                );
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public void reserve(ReservationRequest request,UUID userId) {
        List<SeatLock> seatLocks = seatLockRepository.findByReservationId(request.reservationId());
        Optional<ZoneLock> zoneLockOpt = zoneLockRepository.findByReservationId(request.reservationId());
        List<TicketReservationDto> tickets = new ArrayList<>();
        BeginReservationEvent event = new BeginReservationEvent(request.eventId(), UUID.fromString(request.reservationId()), userId, tickets);


        if (zoneLockOpt.isPresent()) {
            ZoneLock zoneLock = zoneLockOpt.get();
            Section section = sectionRepository.findById(zoneLock.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Section", zoneLock.getZoneId()));
            for (int i = 0; i < zoneLock.getQuantity(); i++) {
                tickets.add(new TicketReservationDto(section.getPrice(), null, null, section.getName()));
            }
            eventPublisher.publishEvent(event);
            return;
        }

        if (!seatLocks.isEmpty()) {
            List<UUID> seatIds = seatLocks.stream()
                .map(SeatLock::getSeatId)
                .toList();
            List<Seat> seats = seatRepository.findByIdsWithSection(seatIds);
            for (Seat seat : seats) {
                tickets.add(new TicketReservationDto(seat.getSection().getPrice(), seat.getRow(), seat.getLable(), seat.getSection().getName()));
            }
            eventPublisher.publishEvent(event);
            return;
        }

        throw new LockExpiredException(request.reservationId());
    }
}
