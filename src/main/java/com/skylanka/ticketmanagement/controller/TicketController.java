package com.skylanka.ticketmanagement.controller;

import com.skylanka.ticketmanagement.dto.request.GenerateTicketRequest;
import com.skylanka.ticketmanagement.dto.request.ReissueTicketRequest;
import com.skylanka.ticketmanagement.dto.request.ValidateTicketRequest;
import com.skylanka.ticketmanagement.dto.request.VoidTicketRequest;
import com.skylanka.ticketmanagement.dto.response.ApiResponse;
import com.skylanka.ticketmanagement.dto.response.TicketResponse;
import com.skylanka.ticketmanagement.dto.response.TicketValidationResponse;
import com.skylanka.ticketmanagement.entity.Ticket;
import com.skylanka.ticketmanagement.enums.TicketStatus;
import com.skylanka.ticketmanagement.service.PdfService;
import com.skylanka.ticketmanagement.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Ticket Management endpoints.
 *
 * Role annotations:
 *   @PreAuthorize("hasRole('AIRLINE_OPERATIONS_OFFICER') or hasRole('SYSTEM_ADMINISTRATOR')")
 *   @PreAuthorize("hasRole('CUSTOMER') or hasRole('AIRLINE_OPERATIONS_OFFICER') or hasRole('SYSTEM_ADMINISTRATOR')")
 *
 * The authenticated user is always extracted from the JWT (via SecurityUtils),
 * never from the request body, preventing IDOR attacks.
 */
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Ticket Management", description = "APIs for SkyLanka Air Travels e-ticket management")
@SecurityRequirement(name = "bearerAuth")
public class TicketController {

    private final TicketService ticketService;
    private final PdfService    pdfService;

    public TicketController(TicketService ticketService, PdfService pdfService) {
        this.ticketService = ticketService;
        this.pdfService    = pdfService;
    }

    // =========================================================================
    // POST /api/tickets/generate
    // =========================================================================

    @PostMapping("/generate")
    @PreAuthorize("hasRole('AIRLINE_OPERATIONS_OFFICER') or hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(summary = "Generate a ticket after successful payment")
    public ResponseEntity<ApiResponse<TicketResponse>> generateTicket(
            @Valid @RequestBody GenerateTicketRequest request,
            HttpServletRequest httpRequest) {

        TicketResponse ticket = ticketService.generateTicket(request, getClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ticket generated successfully.", ticket));
    }

    // =========================================================================
    // GET /api/tickets/my-tickets
    // (must appear BEFORE /{ticketId} to avoid route shadowing)
    // =========================================================================

    @GetMapping("/my-tickets")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get all tickets for the authenticated customer")
    public ResponseEntity<ApiResponse<Page<TicketResponse>>> getMyTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<TicketResponse> tickets = ticketService.getMyTickets(status, page, size);
        return ResponseEntity.ok(ApiResponse.success(tickets));
    }

    // =========================================================================
    // POST /api/tickets/validate
    // =========================================================================

    @PostMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Validate a ticket using its QR code token")
    public ResponseEntity<ApiResponse<TicketValidationResponse>> validateTicket(
            @Valid @RequestBody ValidateTicketRequest request,
            HttpServletRequest httpRequest) {

        TicketValidationResponse result =
                ticketService.validateTicket(request, getClientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // =========================================================================
    // GET /api/tickets/booking/{bookingId}
    // =========================================================================

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all ticket versions for a booking")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> getTicketsByBooking(
            @PathVariable UUID bookingId) {

        List<TicketResponse> tickets = ticketService.getTicketsByBooking(bookingId);
        return ResponseEntity.ok(ApiResponse.success(tickets));
    }

    // =========================================================================
    // GET /api/tickets/{ticketId}
    // =========================================================================

    @GetMapping("/{ticketId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a ticket by its ID")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketById(
            @PathVariable UUID ticketId,
            HttpServletRequest httpRequest) {

        TicketResponse ticket = ticketService.getTicketById(ticketId, getClientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(ticket));
    }

    // =========================================================================
    // GET /api/tickets/{ticketId}/pdf
    // =========================================================================

    @GetMapping("/{ticketId}/pdf")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download the e-ticket as PDF")
    public ResponseEntity<byte[]> downloadTicketPdf(
            @PathVariable UUID ticketId,
            HttpServletRequest httpRequest) {

        Ticket ticket = ticketService.getTicketForPdf(ticketId, getClientIp(httpRequest));
        byte[] pdfBytes = pdfService.generateTicketPdf(ticket);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData(
                "attachment",
                "ticket-" + ticket.getTicketNumber() + ".pdf");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    // =========================================================================
    // POST /api/tickets/{ticketId}/reissue
    // =========================================================================

    @PostMapping("/{ticketId}/reissue")
    @PreAuthorize("hasRole('AIRLINE_OPERATIONS_OFFICER') or hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(summary = "Reissue a ticket (Airline Operations Officers only)")
    public ResponseEntity<ApiResponse<TicketResponse>> reissueTicket(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ReissueTicketRequest request,
            HttpServletRequest httpRequest) {

        TicketResponse newTicket =
                ticketService.reissueTicket(ticketId, request, getClientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Ticket reissued successfully.", newTicket));
    }

    // =========================================================================
    // POST /api/tickets/{ticketId}/void
    // =========================================================================

    @PostMapping("/{ticketId}/void")
    @PreAuthorize("hasRole('AIRLINE_OPERATIONS_OFFICER') or hasRole('SYSTEM_ADMINISTRATOR')")
    @Operation(summary = "Void a ticket (Airline Operations Officers only)")
    public ResponseEntity<ApiResponse<TicketResponse>> voidTicket(
            @PathVariable UUID ticketId,
            @Valid @RequestBody VoidTicketRequest request,
            HttpServletRequest httpRequest) {

        TicketResponse voided =
                ticketService.voidTicket(ticketId, request, getClientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Ticket voided successfully.", voided));
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return (xfHeader != null) ? xfHeader.split(",")[0].trim() : request.getRemoteAddr();
    }
}
