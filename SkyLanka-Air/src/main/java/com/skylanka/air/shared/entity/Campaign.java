package com.skylanka.air.shared.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @Size(max = 500)
    @Column(length = 500)
    private String description;

    private LocalDate startDate;
    private LocalDate endDate;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        description = v;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate v) {
        startDate = v;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate v) {
        endDate = v;
    }

    public boolean isActive() {
        LocalDate today = LocalDate.now();
        boolean afterStart = startDate == null || !today.isBefore(startDate);
        boolean beforeEnd = endDate == null || !today.isAfter(endDate);
        return afterStart && beforeEnd;
    }
}
