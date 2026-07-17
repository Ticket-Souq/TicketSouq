package org.ticketsouq.eventservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.eventservice.dto.*;
import org.ticketsouq.eventservice.exception.*;
import org.ticketsouq.eventservice.model.*;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.model.enums.EventStatus;
import org.ticketsouq.eventservice.model.enums.SeatStatus;
import org.ticketsouq.eventservice.repository.*;
import org.ticketsouq.sharedmodule.GeneralExceptions.ConflictException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LockService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final SeatLockRepository seatLockRepository;
    @Value("${app.lock.ttl:10}")
    private int lockTtlMinutes;

    @Transactional
    public LockSeatsResponse acquireSeatLocks(UUID eventId, LockSeatsRequest request) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new EventNotFoundException(eventId));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException("Only published events can be locked.");
        }
        if (event.getBookingModel() != BookingModel.SEAT) {
            throw new InvalidEventTypeException(BookingModel.SEAT);
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

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(lockTtlMinutes);
        List<SeatLock> locks = request.seatIds().stream()
            .map(seatId -> SeatLock.builder()
                .seatId(seatId)
                .reservationId(request.reservationId())
                .expiresAt(expiresAt)
                .build())
            .toList();
        seatLockRepository.saveAll(locks);

        return new LockSeatsResponse("LOCKED", expiresAt, request.seatIds());
    }

