package com.skylanka.air.shared.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String origin;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String destination;

    @Positive
    private Integer distanceKm;

    @Positive
    private Integer standardDurationMinutes;

    public Long getId() {
        return id;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String v) {
        origin = v;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String v) {
        destination = v;
    }

    public Integer getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Integer v) {
        distanceKm = v;
    }

    public Integer getStandardDurationMinutes() {
        return standardDurationMinutes;
    }

    public void setStandardDurationMinutes(Integer v) {
        standardDurationMinutes = v;
    }
}
