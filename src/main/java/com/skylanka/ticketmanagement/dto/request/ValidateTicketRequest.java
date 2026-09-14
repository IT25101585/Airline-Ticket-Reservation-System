package com.skylanka.ticketmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/tickets/validate
 */
public class ValidateTicketRequest {

    @NotBlank(message = "QR code token is required")
    private String qrCodeToken;

    public String getQrCodeToken() { return qrCodeToken; }
    public void setQrCodeToken(String qrCodeToken) { this.qrCodeToken = qrCodeToken; }
}
