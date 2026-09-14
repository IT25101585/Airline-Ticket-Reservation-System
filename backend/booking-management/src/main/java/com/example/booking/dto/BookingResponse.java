//output dto-->controls what data is returned

package com.example.booking.dto;

import com.example.booking.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder

//returns these to the client
public class BookingResponse {
    private Long id;
    private String bookingReference;
    private BookingStatus status;
    private Long customerId;
    private Long flightId;
    private Long seatId;
    private String paymentId;
    private String ticketId;
    private BigDecimal totalFare;
    private List<PassengerResponse> passengers;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime seatHoldExpiresAt;
}