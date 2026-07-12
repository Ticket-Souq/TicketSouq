package org.ticketsouq.eventservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.eventservice.dto.SeatRequest;
import org.ticketsouq.eventservice.dto.SeatResponse;
import org.ticketsouq.eventservice.model.Seat;
import org.ticketsouq.eventservice.model.Section;
import org.ticketsouq.eventservice.model.enums.BookingModel;
import org.ticketsouq.eventservice.repository.SeatRepository;
import org.ticketsouq.eventservice.repository.SectionRepository;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.sharedmodule.GeneralExceptions.ResourceNotFoundException;

import org.ticketsouq.eventservice.model.enums.SeatStatus;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final SectionRepository sectionRepository;

    @Transactional
    public SeatResponse create(UUID sectionId, SeatRequest request) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));
        if (section.getEvent().getBookingModel() == BookingModel.ZONE) {
            throw new BusinessException("Cannot add seats to a section in a ZONE event", HttpStatus.BAD_REQUEST);
        }
        Seat seat = Seat.builder()
                .section(section)
                .row(request.row())
                .col(request.col())
                .lable(request.lable())
                .status(request.status())
                .price(request.price())
                .build();
        return SeatResponse.from(seatRepository.save(seat));
    }

    @Transactional(readOnly = true)
    public List<SeatResponse> getBySectionId(UUID sectionId) {
        return seatRepository.findBySection_Id(sectionId).stream()
                .map(SeatResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SeatResponse getById(UUID id) {
        return SeatResponse.from(seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", id)));
    }

    @Transactional
    public SeatResponse update(UUID id, SeatRequest request) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", id));
        seat.setRow(request.row());
        seat.setCol(request.col());
        seat.setLable(request.lable());
        seat.setStatus(request.status());
        seat.setPrice(request.price());
        return SeatResponse.from(seatRepository.save(seat));
    }

    @Transactional
    public void delete(UUID id) {
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", id));
        seatRepository.delete(seat);
    }

    @Transactional
    public void lockSeats(List<UUID> seatIds) {
        List<Seat> seats = seatRepository.findAllById(seatIds);
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.LOCKED);
        }
    }

    @Transactional
    public void unlockSeats(List<UUID> seatIds) {
        List<Seat> seats = seatRepository.findAllById(seatIds);
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.AVAILABLE);
        }
    }
}
