package com.skylanka.air.ticket.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.booking.entity.Passenger;
import com.skylanka.air.shared.entity.TicketStatus;
import com.skylanka.air.ticket.entity.Ticket;
import com.skylanka.air.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class TicketService {
    private final TicketRepository repo;

    public TicketService(TicketRepository r) {
        repo = r;
    }

    public List<Ticket> findByBookingId(Long bookingId) {
        return repo.findByBookingId(bookingId);
    }

    public Optional<Ticket> findByPassengerId(Long passengerId) {
        return repo.findByPassengerId(passengerId);
    }

    public Optional<Ticket> findByTicketNumber(String ticketNumber) {
        return repo.findByTicketNumber(ticketNumber);
    }

    public List<Ticket> issue(Booking b) {
        List<Ticket> result = new ArrayList<>();
        for (Passenger p : b.getPassengers()) {
            Ticket t = repo.findByPassengerId(p.getId()).orElseGet(() -> {
                Ticket nt = new Ticket();
                nt.setBooking(b);
                nt.setPassenger(p);
                nt.setTicketNumber(newTicketNumber());
                nt.setQrValue(b.getReference() + "|" + p.getId() + "|" + nt.getTicketNumber());
                return repo.save(nt);
            });
            result.add(t);
        }
        return result;
    }

    public void voidTicket(Booking b) {
        for (Ticket t : repo.findByBookingId(b.getId())) {
            if (t.getStatus() == TicketStatus.ISSUED) {
                t.setStatus(TicketStatus.VOID);
                repo.save(t);
            }
        }
    }

    /** Voids the live ticket of one passenger (e.g. after a no-show). */
    public void voidForPassenger(Passenger p) {
        repo.findByPassengerId(p.getId()).ifPresent(t -> {
            if (t.getStatus() == TicketStatus.ISSUED) {
                t.setStatus(TicketStatus.VOID);
                repo.save(t);
            }
        });
    }

    /** Voids one ticket by its number (Operations "Void ticket"). Returns false if it wasn't live. */
    public boolean voidByNumber(String ticketNumber) {
        Ticket t = repo.findByTicketNumber(ticketNumber).orElseThrow();
        if (t.getStatus() != TicketStatus.ISSUED) return false;
        t.setStatus(TicketStatus.VOID);
        repo.save(t);
        return true;
    }

    /**
     * Reissues a passenger's ticket after a booking modification: the current ticket is marked
     * REISSUED and a new ticket (new number and QR) is created that points back at it via
     * {@code reissuedFrom}. Returns empty when the passenger has no live ticket.
     */
    public Optional<Ticket> reissue(Passenger p) {
        Optional<Ticket> current = repo.findByPassengerId(p.getId());
        if (current.isEmpty() || current.get().getStatus() != TicketStatus.ISSUED) {
            return Optional.empty();
        }
        Ticket old = current.get();
        old.setStatus(TicketStatus.REISSUED);
        repo.save(old);

        Ticket nt = new Ticket();
        nt.setBooking(old.getBooking());
        nt.setPassenger(p);
        nt.setReissuedFrom(old);
        nt.setTicketNumber(newTicketNumber());
        nt.setQrValue(old.getBooking().getReference() + "|" + p.getId() + "|" + nt.getTicketNumber());
        return Optional.of(repo.save(nt));
    }

    /** After a no-show was voided, a genuine check-in of a confirmed booking gets a fresh ticket. */
    public Optional<Ticket> restoreAfterNoShow(Passenger p) {
        Optional<Ticket> current = repo.findByPassengerId(p.getId());
        if (current.isEmpty() || current.get().getStatus() != TicketStatus.VOID) return Optional.empty();
        Ticket old = current.get();
        if (old.getBooking().getStatus() != com.skylanka.air.shared.entity.BookingStatus.CONFIRMED) {
            return Optional.empty();
        }
        Ticket nt = new Ticket();
        nt.setBooking(old.getBooking());
        nt.setPassenger(p);
        nt.setReissuedFrom(old);
        nt.setTicketNumber(newTicketNumber());
        nt.setQrValue(old.getBooking().getReference() + "|" + p.getId() + "|" + nt.getTicketNumber());
        return Optional.of(repo.save(nt));
    }

    /** Reissues by ticket number (Operations "Reissue ticket"). */
    public Optional<Ticket> reissueByNumber(String ticketNumber) {
        Ticket t = repo.findByTicketNumber(ticketNumber).orElseThrow();
        return reissue(t.getPassenger());
    }

    private static String newTicketNumber() {
        return "TKT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /**
     * Builds the e-ticket PDF. Only values already stored for the ticket/booking/flight are printed;
     * layout lives in {@link com.skylanka.air.shared.pdf.TicketPdf}.
     */
    public byte[] pdf(Ticket t) throws IOException {
        Booking b = t.getBooking();
        Passenger holder = t.getPassenger();
        var f = b.getFlight();

        // Older database rows may have been created before QR support was enabled.
        // Always derive a validation value before rendering so every downloadable
        // ticket contains a visible, scannable QR code.
        String validationCode = t.getQrValue();
        if (validationCode == null || validationCode.isBlank()) {
            validationCode = b.getReference() + "|" + holder.getId() + "|" + t.getTicketNumber();
            t.setQrValue(validationCode);
            repo.save(t);
        }

        var dep = f.getDepartureAirport();
        var arr = f.getArrivalAirport();
        var dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH);
        var stampFmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", java.util.Locale.ENGLISH);
        var timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

        String seatNumber = holder.getSeat() != null ? holder.getSeat().getSeatNumber() : "-";
        String seatClass = holder.getSeat() != null && holder.getSeat().getSeatClass() != null
                ? holder.getSeat().getSeatClass() : b.getSeatClass();

        String flightStatus = f.getStatus() == null ? null : f.getStatus().name();
        if (flightStatus != null && f.getDelayMinutes() != null && f.getDelayMinutes() > 0) {
            flightStatus += " (" + f.getDelayMinutes() + " min delay)";
        }

        List<com.skylanka.air.shared.pdf.TicketPdfData.Companion> companions = new ArrayList<>();
        if (b.getPassengerCount() > 1) {
            for (Passenger p : b.getPassengers()) {
                if (p.getId().equals(holder.getId())) continue;
                companions.add(new com.skylanka.air.shared.pdf.TicketPdfData.Companion(
                        p.getName(), p.getSeat() != null ? p.getSeat().getSeatNumber() : "-"));
            }
        }

        var data = new com.skylanka.air.shared.pdf.TicketPdfData(
                b.getReference(), String.valueOf(b.getId()),
                b.getCreatedAt() == null ? null : b.getCreatedAt().format(stampFmt),
                b.getStatus() == null ? null : b.getStatus().name(),
                holder.getName(), String.valueOf(holder.getId()), holder.getPassportNumber(),
                holder.getContact(), b.getCustomer() != null ? b.getCustomer().getEmail() : null,
                f.getFlightNumber(), f.getAirline(), f.getAircraft(), flightStatus,
                dep != null ? dep.getAirportCode() : null, dep != null ? dep.getCity() : f.getOrigin(),
                dep != null ? dep.getName() : null,
                arr != null ? arr.getAirportCode() : null, arr != null ? arr.getCity() : f.getDestination(),
                arr != null ? arr.getName() : null,
                f.getDepartureDate() == null ? null : f.getDepartureDate().format(dateFmt),
                f.getDepartureTime() == null ? null : f.getDepartureTime().format(timeFmt),
                f.getArrivalTime() == null ? null : f.getArrivalTime().format(timeFmt),
                seatNumber, seatClass,
                t.getTicketNumber(), t.getStatus() == null ? null : t.getStatus().name(),
                t.getIssuedAt() == null ? null : t.getIssuedAt().format(dateFmt),
                t.getReissuedFrom() != null ? t.getReissuedFrom().getTicketNumber() : null,
                validationCode,
                "LKR", money(b.getBaseFare()), money(b.getTax()), money(b.getServiceFee()),
                b.getDiscount() != null && b.getDiscount().signum() > 0 ? "-" + money(b.getDiscount()) : null,
                money(b.getTotalFare()), b.getTotalFare() == null ? null : money(perPassengerShare(b)),
                b.getPassengerCount(), companions);

        return com.skylanka.air.shared.pdf.TicketPdf.render(data);
    }

    private static String money(java.math.BigDecimal v) {
        return v == null ? null : String.format(java.util.Locale.US, "%,.2f", v);
    }

    private java.math.BigDecimal perPassengerShare(Booking b) {
        int count = Math.max(1, b.getPassengerCount());
        return b.getTotalFare().divide(
                java.math.BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);
    }
}
