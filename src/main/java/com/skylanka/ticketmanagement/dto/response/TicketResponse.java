package com.skylanka.ticketmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.skylanka.ticketmanagement.enums.TicketStatus;
import com.skylanka.ticketmanagement.enums.TicketType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO returned by ticket endpoints.
 * Does NOT include raw payment information or sensitive PII.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketResponse {

    private UUID id;
    private String ticketNumber;
    private int ticketVersion;
    private TicketStatus ticketStatus;
    private TicketType ticketType;

    // --- Booking ---
    private UUID bookingId;
    private String bookingReference;

    // --- Passenger ---
    private UUID passengerId;
    private String passengerName;
    private String maskedPassportNumber;

    // --- User ---
    private UUID userId;

    // --- Flight ---
    private UUID flightId;
    private String flightNumber;
    private String originCode;
    private String originName;
    private String destinationCode;
    private String destinationName;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;

    // --- Seat ---
    private UUID seatId;
    private String seatNumber;
    private String cabinClass;

    // --- QR Code ---
    private String qrCodeToken;
    private String qrCodeData;   // Base64 PNG — omitted in list responses

    // --- Validity ---
    private LocalDateTime issuedAt;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    // --- Versioning ---
    private UUID reissuedFromTicketId;
    private String reissueReason;
    private String voidReason;
    private LocalDateTime cancelledAt;

    // --- Audit ---
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== Getters / Setters =====

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }

    public int getTicketVersion() { return ticketVersion; }
    public void setTicketVersion(int ticketVersion) { this.ticketVersion = ticketVersion; }

    public TicketStatus getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(TicketStatus ticketStatus) { this.ticketStatus = ticketStatus; }

    public TicketType getTicketType() { return ticketType; }
    public void setTicketType(TicketType ticketType) { this.ticketType = ticketType; }

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }

    public UUID getPassengerId() { return passengerId; }
    public void setPassengerId(UUID passengerId) { this.passengerId = passengerId; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public String getMaskedPassportNumber() { return maskedPassportNumber; }
    public void setMaskedPassportNumber(String maskedPassportNumber) { this.maskedPassportNumber = maskedPassportNumber; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }

    public String getOriginCode() { return originCode; }
    public void setOriginCode(String originCode) { this.originCode = originCode; }

    public String getOriginName() { return originName; }
    public void setOriginName(String originName) { this.originName = originName; }

    public String getDestinationCode() { return destinationCode; }
    public void setDestinationCode(String destinationCode) { this.destinationCode = destinationCode; }

    public String getDestinationName() { return destinationName; }
    public void setDestinationName(String destinationName) { this.destinationName = destinationName; }

    public LocalDateTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalDateTime departureTime) { this.departureTime = departureTime; }

    public LocalDateTime getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(LocalDateTime arrivalTime) { this.arrivalTime = arrivalTime; }

    public UUID getSeatId() { return seatId; }
    public void setSeatId(UUID seatId) { this.seatId = seatId; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public String getCabinClass() { return cabinClass; }
    public void setCabinClass(String cabinClass) { this.cabinClass = cabinClass; }

    public String getQrCodeToken() { return qrCodeToken; }
    public void setQrCodeToken(String qrCodeToken) { this.qrCodeToken = qrCodeToken; }

    public String getQrCodeData() { return qrCodeData; }
    public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }

    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }

    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }

    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }

    public UUID getReissuedFromTicketId() { return reissuedFromTicketId; }
    public void setReissuedFromTicketId(UUID reissuedFromTicketId) { this.reissuedFromTicketId = reissuedFromTicketId; }

    public String getReissueReason() { return reissueReason; }
    public void setReissueReason(String reissueReason) { this.reissueReason = reissueReason; }

    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
