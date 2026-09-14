package com.skylanka.ticketmanagement.entity.stubs;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * STUB entity for the Passenger table.
 * Replace with your teammate's real Passenger entity.
 */
@Getter
@Setter
@Entity
@Table(name = "passengers")
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "passport_number", length = 30)
    private String passportNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String nationality;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    /** Returns masked passport number e.g. ****5678 */
    public String getMaskedPassportNumber() {
        if (passportNumber == null || passportNumber.length() < 4) return "****";
        return "****" + passportNumber.substring(passportNumber.length() - 4);
    }
}
