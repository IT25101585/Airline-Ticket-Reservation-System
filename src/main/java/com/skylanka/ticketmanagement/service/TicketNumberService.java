package com.skylanka.ticketmanagement.service;

import com.skylanka.ticketmanagement.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates unique, human-readable ticket numbers in the format:
 *   SKL-YYYYMMDD-NNNNNN
 *
 * Guarantees uniqueness by:
 *   1. Using a 6-digit random suffix.
 *   2. Checking the DB and retrying on collision (max 10 attempts).
 *   3. The tickets table also has a UNIQUE constraint on ticket_number
 *      as the final guard against race conditions.
 */
@Service
public class TicketNumberService {

    private static final String PREFIX        = "SKL";
    private static final int    SUFFIX_LENGTH = 6;
    private static final int    MAX_RETRIES   = 10;

    private final SecureRandom  random         = new SecureRandom();
    private final TicketRepository ticketRepository;

    public TicketNumberService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Generate a unique ticket number.
     * @throws IllegalStateException if a unique number cannot be generated after MAX_RETRIES
     */
    public String generateUniqueTicketNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String candidate = buildTicketNumber(datePart);
            if (!ticketRepository.existsByTicketNumber(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Failed to generate a unique ticket number after " + MAX_RETRIES + " attempts.");
    }

    private String buildTicketNumber(String datePart) {
        int suffix = random.nextInt(900_000) + 100_000; // always 6 digits (100000–999999)
        return PREFIX + "-" + datePart + "-" + suffix;
    }
}
