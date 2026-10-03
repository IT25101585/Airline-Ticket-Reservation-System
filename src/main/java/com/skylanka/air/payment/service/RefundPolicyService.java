package com.skylanka.air.payment.service;

import com.skylanka.air.booking.entity.Booking;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class RefundPolicyService {
    private static final BigDecimal HALF = new BigDecimal("0.50");
    private static final int FULL_REFUND_HOURS = 48;
    private static final int PARTIAL_REFUND_HOURS = 24;
    private static final int DELAY_FULL_REFUND_MINUTES = 24 * 60;

    private final Clock clock;

    public RefundPolicyService() {
        this(Clock.systemDefaultZone());
    }

    RefundPolicyService(Clock clock) {
        this.clock = clock;
    }

    public RefundDecision determine(Booking booking, CancellationReason reason) {
        return determine(booking, reason, LocalDateTime.now(clock));
    }

    RefundDecision determine(
            Booking booking,
            CancellationReason reason,
            LocalDateTime cancellationTime) {

        BigDecimal fare = money(booking.getTotalFare());
        if (reason == CancellationReason.FLIGHT_CANCELLED) {
            return new RefundDecision(fare, "Full refund because the flight was cancelled.");
        }

        Integer delayMinutes = booking.getFlight().getDelayMinutes();
        if (booking.getFlight().getStatus() == com.skylanka.air.shared.entity.FlightStatus.DELAYED
                && delayMinutes != null
                && delayMinutes >= DELAY_FULL_REFUND_MINUTES) {

            return new RefundDecision(
                    fare,
                    "Full refund because the flight was delayed by more than 24 hours."
            );
        }

        LocalDateTime departure = LocalDateTime.of(
                booking.getFlight().getDepartureDate(),
                booking.getFlight().getDepartureTime()
        );
        long hoursUntilDeparture =
                java.time.Duration.between(cancellationTime, departure).toHours();

        if (hoursUntilDeparture > FULL_REFUND_HOURS) {
            return new RefundDecision(fare, "Full refund for cancellation more than 48 hours before departure.");
        }
        if (hoursUntilDeparture > PARTIAL_REFUND_HOURS) {
            return new RefundDecision(
                    fare.multiply(HALF).setScale(2, RoundingMode.HALF_UP),
                    "50% refund for cancellation between 24 and 48 hours before departure."
            );
        }
        return new RefundDecision(
                BigDecimal.ZERO.setScale(2),
                "No refund for cancellation within 24 hours of departure."
        );
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    public enum CancellationReason {
        CUSTOMER_REQUEST,
        FLIGHT_CANCELLED
    }

    public record RefundDecision(BigDecimal amount, String explanation) {
        public boolean refundable() {
            return amount != null && amount.signum() > 0;
        }
    }
}