package com.skylanka.air.seat.service;

import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.seat.entity.Seat;
import com.skylanka.air.seat.repository.SeatRepository;
import com.skylanka.air.shared.entity.SeatStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SeatService {
    private final SeatRepository repo;

    private static final int DEFAULT_HOLD_MINUTES = 10;

    private com.skylanka.air.shared.service.SettingsService settings;

    public SeatService(SeatRepository r) {
        repo = r;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSettings(com.skylanka.air.shared.service.SettingsService settings) {
        this.settings = settings;
    }

    public int holdMinutes() {
        return settings == null
                ? DEFAULT_HOLD_MINUTES
                : settings.getInt(com.skylanka.air.shared.service.SettingsService.SEAT_HOLD_MINUTES, DEFAULT_HOLD_MINUTES);
    }

    /** Result of trying to temporarily hold a seat on the seat map. */
    public record HoldResult(boolean held, String message, boolean limitReached) {
        public HoldResult(boolean held, String message) {
            this(held, message, false);
        }
    }

    /** Most seats one visitor (signed in or guest session) may hold at once; also the largest party per booking. */
    public static final int MAX_SEATS_PER_HOLDER = 9;

    /**
     * Temporarily blocks a seat for {@code holder} while they complete the booking. If the seat was
     * already taken (or held by someone else whose hold is still live) the caller is told so and the
     * seat map should be refreshed.
     */
    @org.springframework.transaction.annotation.Transactional
    public HoldResult hold(Long seatId, String holder) {
        Seat s = repo.findByIdForUpdate(seatId).orElse(null);
        if (s == null) return new HoldResult(false, "Seat not found.");
        releaseIfLapsed(s);
        if (s.getStatus() == SeatStatus.HELD && holder.equals(s.getHeldBy())) {
            s.setHeldAt(java.time.LocalDateTime.now()); // refresh the hold
            repo.save(s);
            return new HoldResult(true, "Seat held.");
        }
        if (s.getStatus() != SeatStatus.AVAILABLE) {
            return new HoldResult(false, "Seat " + s.getSeatNumber() + " was just taken. The seat map has been refreshed.");
        }
        long alreadyHeld = repo.countByStatusAndHeldByAndHeldAtAfter(
                SeatStatus.HELD, holder, java.time.LocalDateTime.now().minusMinutes(holdMinutes()));
        if (alreadyHeld >= MAX_SEATS_PER_HOLDER) {
            return new HoldResult(false,
                    "You can hold at most " + MAX_SEATS_PER_HOLDER + " seats at a time. Complete or release a booking first.",
                    true);
        }
        s.setStatus(SeatStatus.HELD);
        s.setHeldBy(holder);
        s.setHeldAt(java.time.LocalDateTime.now());
        repo.save(s);
        return new HoldResult(true, "Seat " + s.getSeatNumber() + " held for " + holdMinutes() + " minutes.");
    }

    /** Releases a hold the holder owns (customer changed their mind / picked another seat). */
    @org.springframework.transaction.annotation.Transactional
    public void release(Long seatId, String holder) {
        repo.findByIdForUpdate(seatId).ifPresent(s -> {
            if (s.getStatus() == SeatStatus.HELD && holder.equals(s.getHeldBy())) {
                clearHold(s);
                repo.save(s);
            }
        });
    }

    /** Scheduler entry point: frees every hold older than the configured timeout. Returns how many. */
    @org.springframework.transaction.annotation.Transactional
    public int releaseExpiredHolds() {
        var cutoff = java.time.LocalDateTime.now().minusMinutes(holdMinutes());
        var lapsed = repo.findByStatusAndHeldByIsNotNullAndHeldAtBefore(SeatStatus.HELD, cutoff);
        lapsed.forEach(s -> {
            clearHold(s);
            repo.save(s);
        });
        return lapsed.size();
    }

    /** Seat id -> status for the live seat map (lapsed holds already reported as AVAILABLE). */
    public Map<Long, String> statusMap(Long flightId, String viewer) {
        Map<Long, String> out = new LinkedHashMap<>();
        var cutoff = java.time.LocalDateTime.now().minusMinutes(holdMinutes());
        for (Seat s : repo.findByFlightIdOrderBySeatNumber(flightId)) {
            String status = s.getStatus().name();
            if (s.getStatus() == SeatStatus.HELD) {
                if (viewer != null && viewer.equals(s.getHeldBy())) status = "HELD_BY_YOU";
                else if (s.getHeldAt() == null || s.getHeldAt().isBefore(cutoff)) status = "AVAILABLE";
            }
            out.put(s.getId(), status);
        }
        return out;
    }

    private void releaseIfLapsed(Seat s) {
        if (s.getStatus() == SeatStatus.HELD
                && (s.getHeldAt() == null
                || s.getHeldAt().isBefore(java.time.LocalDateTime.now().minusMinutes(holdMinutes())))) {
            clearHold(s);
        }
    }

    private void clearHold(Seat s) {
        s.setStatus(SeatStatus.AVAILABLE);
        s.setHeldBy(null);
        s.setHeldAt(null);
    }

    public List<Seat> forFlight(Long id) {
        return repo.findByFlightIdOrderBySeatNumber(id);
    }

    public List<List<Seat>> groupByRow(List<Seat> flightSeats) {
        Map<Integer, List<Seat>> byRow = new TreeMap<>();
        for (Seat s : flightSeats) {
            int row = Integer.parseInt(s.getSeatNumber().replaceAll("[^0-9]", ""));
            byRow.computeIfAbsent(row, k -> new ArrayList<>()).add(s);
        }
        List<List<Seat>> rows = new ArrayList<>();
        for (List<Seat> rowSeats : byRow.values()) {
            rowSeats.sort(Comparator.comparing(s -> s.getSeatNumber().replaceAll("[0-9]", "")));
            rows.add(rowSeats);
        }
        return rows;
    }

    public Seat getAvailable(Long id) {
        Seat s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Seat not found."));
        if (s.getStatus() != SeatStatus.AVAILABLE)
            throw new IllegalStateException("That seat is no longer available. Please choose another seat.");
        return s;
    }

    public void createSeats(Flight f) {
        createSeats(f, 6, 6);
    }

    /**
     * Generates the seat map for a flight from an aircraft layout: {@code seatsPerRow} letters per row
     * (A, B, C ...) with the first {@code businessSeats} seats in business class. The defaults (6 and 6)
     * reproduce the layout used before layouts became configurable.
     */
    public void createSeats(Flight f, int seatsPerRow, int businessSeats) {
        int perRow = Math.max(2, Math.min(10, seatsPerRow));
        int business = Math.max(0, Math.min(businessSeats, f.getSeatCapacity()));
        for (int i = 1; i <= f.getSeatCapacity(); i++) {
            Seat s = new Seat();
            s.setFlight(f);
            s.setSeatNumber(((i - 1) / perRow + 1) + String.valueOf((char) ('A' + (i - 1) % perRow)));
            s.setSeatClass(i <= business ? "BUSINESS" : "ECONOMY");
            repo.save(s);
        }
    }

    public void ensureSeats(Flight f) {
        if (repo.countByFlightId(f.getId()) == 0) {
            createSeats(f);
        }
    }

    public void setStatus(Long id, SeatStatus status) {
        Seat s = repo.findById(id).orElseThrow();
        if (s.getStatus() == SeatStatus.BOOKED && status != SeatStatus.BOOKED)
            throw new IllegalStateException("Booked seats can only be released by cancelling the booking.");
        if (s.getStatus() == SeatStatus.BLOCKED && status == SeatStatus.BOOKED)
            throw new IllegalStateException("Blocked seats cannot be booked.");
        s.setStatus(status);
        if (status != SeatStatus.HELD) {
            s.setHeldBy(null);
            s.setHeldAt(null);
        }
        repo.save(s);
    }
}
