package com.skylanka.air.shared.pdf;

import java.util.List;

/** Plain, pre-formatted values for the payment receipt PDF (money strings carry no currency). */
public record ReceiptPdfData(
        String receiptNumber, String receiptDate,
        String customerName, String email, String contact,
        String bookingReference, String bookingId, String bookingDate, String bookingStatus,
        String flightNumber, String airline, String route, String travelDate, String departureTime,
        String seatClass, List<String> passengers,
        /** "CARD", "BANK_TRANSFER" or "OTHER". */
        String methodKind, String methodLabel, String transactionId, String paymentDate, String paymentStatus,
        String currency, String baseFare, String tax, String serviceFee, String discount, String promoCode,
        String totalPaid, String refunded) {
}
