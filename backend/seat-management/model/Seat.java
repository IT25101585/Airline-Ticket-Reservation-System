package com.example.demoseat.model;

public class Seat {
    private String seatId;
    private String flightId;
    private String cabinClass;
    private double price;
    private boolean booked;

    public Seat() {
    }


    public Seat(String seatId, String flightId, String cabinClass, double price, boolean booked) {
        this.seatId = seatId;
        this.flightId = flightId;
        this.cabinClass = cabinClass;
        this.price = price;
        this.booked = booked;
    }

    public String getSeatId() {
        return seatId;
    }

    public void setSeatId(String seatId) {
        this.seatId = seatId;
    }

    public String getFlightId() {
        return flightId;
    }

    public void setFlightId(String flightId) {
        this.flightId = flightId;
    }

    public String getCabinClass() {
        return cabinClass;
    }

    public void setCabinClass(String cabinClass) {
        this.cabinClass = cabinClass;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public boolean isBooked() {
        return booked;
    }

    public void setBooked(boolean booked) {
        this.booked = booked;
    }
}