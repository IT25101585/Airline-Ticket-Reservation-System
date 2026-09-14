//Represents data coming into the API
//Separates data sent by the client from the database entity
//Database has additional stuff (timestamps etc.)

package com.example.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {

    //sent from the frontend as JSON-->converted into CreateBookingRequest object
    @NotNull(message = "customerId is required")
    private Long customerId;

    @NotNull(message = "flightId is required")
    private Long flightId;

    @NotNull(message = "seatId is required")
    private Long seatId;

    @NotBlank(message = "passenger full name is required")
    private String passengerName;

    @NotBlank(message = "passport number is required")
    private String passportNumber;

    @NotBlank(message = "contact number is required")
    private String contactNumber;
}