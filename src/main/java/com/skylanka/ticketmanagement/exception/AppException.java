package com.skylanka.ticketmanagement.exception;

import org.springframework.http.HttpStatus;

/**
 * Base application exception that carries an HTTP status and a machine-readable error code.
 */
public class AppException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final ErrorCode errorCode;

    public AppException(HttpStatus httpStatus, ErrorCode errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode  = errorCode;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public ErrorCode  getErrorCode()  { return errorCode;  }

    // --- factory helpers ---

    public static AppException bookingNotFound(String bookingId) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND,
                "Booking not found: " + bookingId);
    }

    public static AppException paymentNotCompleted() {
        return new AppException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.PAYMENT_NOT_COMPLETED,
                "Payment has not been completed. Ticket cannot be generated.");
    }

    public static AppException ticketAlreadyExists(String bookingId) {
        return new AppException(HttpStatus.CONFLICT, ErrorCode.TICKET_ALREADY_EXISTS,
                "An ACTIVE ticket already exists for booking: " + bookingId);
    }

    public static AppException ticketNotFound(String ticketId) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.TICKET_NOT_FOUND,
                "Ticket not found: " + ticketId);
    }

    public static AppException invalidStatusTransition(String from, String to) {
        return new AppException(HttpStatus.CONFLICT, ErrorCode.INVALID_STATUS_TRANSITION,
                "Cannot transition ticket from " + from + " to " + to);
    }

    public static AppException invalidQrToken() {
        return new AppException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_QR_TOKEN,
                "Invalid or expired QR token.");
    }

    public static AppException unauthorizedAccess() {
        return new AppException(HttpStatus.FORBIDDEN, ErrorCode.UNAUTHORIZED_ACCESS,
                "You do not have permission to access this ticket.");
    }

    public static AppException bookingCancelled() {
        return new AppException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.BOOKING_CANCELLED,
                "Booking is cancelled. Ticket cannot be generated.");
    }
}
