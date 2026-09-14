//client sends info they want to modify-->spring turns it into object

package com.example.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModifyBookingRequest {
    private String passengerName;
    private String passportNumber;
    private String contactNumber;
    private Long newFlightId;
    private Long newSeatId;
}