//Business logic

package com.example.booking.service.impl;

import com.example.booking.client.CustomerServiceClient;
import com.example.booking.client.FlightServiceClient;
import com.example.booking.client.PaymentServiceClient;
import com.example.booking.client.SeatServiceClient;
import com.example.booking.client.TicketServiceClient;
import com.example.booking.dto.BookingResponse;
import com.example.booking.dto.CreateBookingRequest;
import com.example.booking.dto.ModifyBookingRequest;
import com.example.booking.dto.PassengerResponse;
import com.example.booking.entity.Booking;
import com.example.booking.entity.BookingPassenger;
import com.example.booking.entity.BookingStatus;
import com.example.booking.exception.BookingNotFoundException;
import com.example.booking.exception.InvalidBookingStateException;
import com.example.booking.exception.SeatUnavailableException;
import com.example.booking.repository.BookingRepository;
import com.example.booking.service.BookingService;
import com.example.booking.util.BookingReferenceGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final long SEAT_HOLD_MINUTES = 30;

    private final BookingRepository bookingRepository;
    private final BookingReferenceGenerator referenceGenerator;

    private final CustomerServiceClient customerServiceClient;
    private final FlightServiceClient flightServiceClient;
    private final SeatServiceClient seatServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final TicketServiceClient ticketServiceClient;

    @Override
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {

        //check customer (if not valid throw error)
        if (!customerServiceClient.isActiveCustomer(request.getCustomerId())) {
            throw new InvalidBookingStateException(
                    "Customer " + request.getCustomerId() + " is not a valid/active account");
        }

        //check flight (unavailable-->error)
        if (!flightServiceClient.isFlightBookable(request.getFlightId())) {
            throw new InvalidBookingStateException(
                    "Flight " + request.getFlightId() + " is not available for booking");
        }

        //check seat (unavailable-->error)
        if (!seatServiceClient.isSeatAvailable(request.getFlightId(), request.getSeatId())) {
            throw new SeatUnavailableException(
                    "Seat " + request.getSeatId() + " is no longer available on this flight");
        }

        //booking ref
        String bookingReference = generateUniqueReference();

        //hold seat
        String seatNumber = seatServiceClient.holdSeat(
                request.getFlightId(), request.getSeatId(), bookingReference);

        //calculate fare
        BigDecimal baseFare = flightServiceClient.getBaseFare(request.getFlightId());
        BigDecimal seatSurcharge = seatServiceClient.getSeatSurcharge(
                request.getFlightId(), request.getSeatId());
        BigDecimal totalFare = baseFare.add(seatSurcharge);

        //create booking object
        Booking booking = Booking.builder()
                .bookingReference(bookingReference)
                .customerId(request.getCustomerId())
                .flightId(request.getFlightId())
                .seatId(request.getSeatId())
                .status(BookingStatus.PENDING)
                .totalFare(totalFare)
                .seatHoldExpiresAt(LocalDateTime.now().plusMinutes(SEAT_HOLD_MINUTES))
                .build();

        //add passenger
        BookingPassenger passenger = BookingPassenger.builder()
                .fullName(request.getPassengerName())
                .passportNumber(request.getPassportNumber())
                .contactNumber(request.getContactNumber())
                .seatNumber(seatNumber)
                .build();
        booking.addPassenger(passenger);

        //save booking
        Booking saved = bookingRepository.save(booking);

        //initiate payment
        String paymentId = paymentServiceClient.initiatePayment(bookingReference, totalFare);
        saved.setPaymentId(paymentId);
        saved = bookingRepository.save(saved);

        //should add notifi service here

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingByReference(String bookingReference) {
        return toResponse(findBookingOrThrow(bookingReference));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingHistory(Long customerId) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    @Override
    @Transactional
    //modify booking logic
    public BookingResponse modifyBooking(String bookingReference, ModifyBookingRequest request) {
        Booking booking = findBookingOrThrow(bookingReference);

        //checks whether modification is allowed
        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new InvalidBookingStateException(
                    "Cannot modify a booking in status " + booking.getStatus());
        }

        //modify passenger details
        if (!booking.getPassengers().isEmpty()) {
            BookingPassenger passenger = booking.getPassengers().get(0);
            if (request.getPassengerName() != null) {
                passenger.setFullName(request.getPassengerName());
            }
            if (request.getPassportNumber() != null) {
                passenger.setPassportNumber(request.getPassportNumber());
            }
            if (request.getContactNumber() != null) {
                passenger.setContactNumber(request.getContactNumber());
            }
        }

        //check if flight/seat changed
        boolean seatOrFlightChanged = request.getNewFlightId() != null || request.getNewSeatId() != null;
        if (seatOrFlightChanged) {

            //determine target flight and search
            Long targetFlightId = request.getNewFlightId() != null ? request.getNewFlightId() : booking.getFlightId();
            Long targetSeatId = request.getNewSeatId() != null ? request.getNewSeatId() : booking.getSeatId();

            //check new flight
            if (!flightServiceClient.isFlightBookable(targetFlightId)) {
                throw new InvalidBookingStateException("Flight " + targetFlightId + " is not available");
            }

            //check new seat
            if (!seatServiceClient.isSeatAvailable(targetFlightId, targetSeatId)) {
                throw new SeatUnavailableException("Seat " + targetSeatId + " is no longer available");
            }

            //release old seat
            seatServiceClient.releaseSeat(booking.getFlightId(), booking.getSeatId(), bookingReference);

            //hold new seat
            String newSeatNumber = seatServiceClient.holdSeat(targetFlightId, targetSeatId, bookingReference);

            //re-calculate fee
            BigDecimal newBaseFare = flightServiceClient.getBaseFare(targetFlightId);
            BigDecimal newSurcharge = seatServiceClient.getSeatSurcharge(targetFlightId, targetSeatId);

            //update booking
            booking.setFlightId(targetFlightId);
            booking.setSeatId(targetSeatId);
            booking.setTotalFare(newBaseFare.add(newSurcharge));
            if (!booking.getPassengers().isEmpty()) {
                booking.getPassengers().get(0).setSeatNumber(newSeatNumber);
            }

            //ticket reissue (note: ticket service is a stub)
            if (booking.getTicketId() != null) {
                String reissued = ticketServiceClient.reissueTicket(bookingReference, booking.getTicketId());
                booking.setTicketId(reissued);
            }
        }

        //save to database (update)
        return toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(String bookingReference) {
        Booking booking = findBookingOrThrow(bookingReference);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidBookingStateException("Booking is already cancelled");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new InvalidBookingStateException("Cannot cancel a completed booking");
        }

        seatServiceClient.releaseSeat(booking.getFlightId(), booking.getSeatId(), bookingReference);

        if (booking.getTicketId() != null) {
            ticketServiceClient.voidTicket(booking.getTicketId());
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED && booking.getPaymentId() != null) {
            paymentServiceClient.requestRefund(bookingReference, booking.getPaymentId(), booking.getTotalFare());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponse confirmBookingAfterPayment(String bookingReference, String paymentId) {
        Booking booking = findBookingOrThrow(bookingReference);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException(
                    "Booking " + bookingReference + " is not awaiting payment (status=" + booking.getStatus() + ")");
        }

        booking.setPaymentId(paymentId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSeatHoldExpiresAt(null);

        seatServiceClient.confirmSeat(booking.getFlightId(), booking.getSeatId(), bookingReference);

        String ticketId = ticketServiceClient.generateTicket(bookingReference);
        booking.setTicketId(ticketId);

        return toResponse(bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingResponse markPaymentFailed(String bookingReference) {
        Booking booking = findBookingOrThrow(bookingReference);
        return toResponse(booking);
    }

    @Override
    @Transactional
    public void expireStaleBookings() {
        List<Booking> stale = bookingRepository.findByStatusAndSeatHoldExpiresAtBefore(
                BookingStatus.PENDING, LocalDateTime.now());

        for (Booking booking : stale) {
            seatServiceClient.releaseSeat(booking.getFlightId(), booking.getSeatId(), booking.getBookingReference());
            booking.setStatus(BookingStatus.EXPIRED);
        }
        bookingRepository.saveAll(stale);
    }

    private Booking findBookingOrThrow(String bookingReference) {
        return bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new BookingNotFoundException(bookingReference));
    }

    //Generate booking ref
    private String generateUniqueReference() {
        String candidate;
        do {
            candidate = referenceGenerator.generate();
        } while (bookingRepository.existsByBookingReference(candidate));
        return candidate;
    }

    private BookingResponse toResponse(Booking booking) {
        List<PassengerResponse> passengerResponses = booking.getPassengers().stream()
                .map(p -> PassengerResponse.builder()
                        .fullName(p.getFullName())
                        .passportNumber(p.getPassportNumber())
                        .contactNumber(p.getContactNumber())
                        .seatNumber(p.getSeatNumber())
                        .build())
                .toList();

        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .status(booking.getStatus())
                .customerId(booking.getCustomerId())
                .flightId(booking.getFlightId())
                .seatId(booking.getSeatId())
                .paymentId(booking.getPaymentId())
                .ticketId(booking.getTicketId())
                .totalFare(booking.getTotalFare())
                .passengers(passengerResponses)
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .seatHoldExpiresAt(booking.getSeatHoldExpiresAt())
                .build();
    }
}