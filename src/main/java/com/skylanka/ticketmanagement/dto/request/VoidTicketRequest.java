package com.skylanka.ticketmanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/tickets/{ticketId}/void
 */
public class VoidTicketRequest {

    @NotBlank(message = "Void reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
