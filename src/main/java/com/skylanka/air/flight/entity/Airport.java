package com.skylanka.air.flight.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "airports")
public class Airport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 5)
    private String airportCode;
    @Column(nullable = false, length = 100)
    private String city;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 100)
    private String country;

    public Airport() {
    }

    public Airport(String airportCode, String city, String name, String country) {
        this.airportCode = airportCode;
        this.city = city;
        this.name = name;
        this.country = country;
    }

    public Long getId() {
        return id;
    }

    public String getAirportCode() {
        return airportCode;
    }

    public void setAirportCode(String v) {
        airportCode = v;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String v) {
        city = v;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String v) {
        country = v;
    }

    public String getFullLocation() {
        return city + " (" + airportCode + ") - " + name;
    }
}
