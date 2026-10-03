package com.skylanka.air.shared.service;

import com.skylanka.air.shared.entity.SystemSetting;
import com.skylanka.air.shared.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Administrator-configurable system settings (Use case: "Configure System Settings").
 * Every setting has a built-in default so the application behaves identically until an
 * administrator changes something.
 */
@Service
public class SettingsService {
    public static final String BOOKING_HOLD_MINUTES = "booking.hold-minutes";
    public static final String SEAT_HOLD_MINUTES = "seat.hold-minutes";
    public static final String TAX_PERCENT = "fare.tax-percent";
    public static final String SERVICE_FEE_PERCENT = "fare.service-fee-percent";

    private final SystemSettingRepository repo;

    public SettingsService(SystemSettingRepository repo) {
        this.repo = repo;
    }

    public String get(String key, String fallback) {
        return repo.findById(key).map(SystemSetting::getValue).orElse(fallback);
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public BigDecimal getDecimal(String key, BigDecimal fallback) {
        try {
            return new BigDecimal(get(key, fallback.toPlainString()).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void set(String key, String value) {
        SystemSetting s = repo.findById(key).orElseGet(() -> new SystemSetting(key, value));
        s.setValue(value);
        repo.save(s);
    }

    /** Validates and stores all four settings; throws IllegalArgumentException with a friendly message. */
    public void update(int bookingHoldMinutes, int seatHoldMinutes, BigDecimal taxPercent, BigDecimal serviceFeePercent) {
        if (bookingHoldMinutes < 5 || bookingHoldMinutes > 240) {
            throw new IllegalArgumentException("Booking hold must be between 5 and 240 minutes.");
        }
        if (seatHoldMinutes < 1 || seatHoldMinutes > 60) {
            throw new IllegalArgumentException("Seat hold must be between 1 and 60 minutes.");
        }
        if (taxPercent == null || taxPercent.signum() < 0 || taxPercent.compareTo(new BigDecimal("50")) > 0) {
            throw new IllegalArgumentException("Tax must be between 0 and 50 percent.");
        }
        if (serviceFeePercent == null || serviceFeePercent.signum() < 0 || serviceFeePercent.compareTo(new BigDecimal("50")) > 0) {
            throw new IllegalArgumentException("Service fee must be between 0 and 50 percent.");
        }
        set(BOOKING_HOLD_MINUTES, String.valueOf(bookingHoldMinutes));
        set(SEAT_HOLD_MINUTES, String.valueOf(seatHoldMinutes));
        set(TAX_PERCENT, taxPercent.stripTrailingZeros().toPlainString());
        set(SERVICE_FEE_PERCENT, serviceFeePercent.stripTrailingZeros().toPlainString());
    }
}
