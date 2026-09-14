package com.example.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerResponse {
    private String fullName;
    private String passportNumber;
    private String contactNumber;
    private String seatNumber;
}