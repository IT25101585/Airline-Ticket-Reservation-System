package com.skylanka.ticketmanagement.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skylanka.ticketmanagement.dto.request.GenerateTicketRequest;
import com.skylanka.ticketmanagement.dto.request.ReissueTicketRequest;
import com.skylanka.ticketmanagement.dto.request.ValidateTicketRequest;
import com.skylanka.ticketmanagement.dto.request.VoidTicketRequest;
import com.skylanka.ticketmanagement.entity.stubs.*;
import com.skylanka.ticketmanagement.enums.*;
import com.skylanka.ticketmanagement.repository.*;
import com.skylanka.ticketmanagement.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for TicketController.
 * Uses H2 in-memory DB (configured in src/test/resources/application.properties).
 * All HTTP calls go through the real filter chain (JWT auth is required).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Ticket Controller Integration Tests")
class TicketControllerIntegrationTest {

    @Autowired private MockMvc           mockMvc;
    @Autowired private ObjectMapper      objectMapper;
    @Autowired private JwtTokenProvider  jwtProvider;

    @Autowired private BookingRepository  bookingRepo;
    @Autowired private PaymentRepository  paymentRepo;

    // Shared state across ordered tests
    private static UUID   bookingId;
    private static UUID   ticketId;
    private static String qrToken;

    // JWT tokens for different roles
    private static String customerToken;
    private static String staffToken;
    private static String otherCustomerToken;

    private static UUID customerId;
    private static UUID staffId;
    private static UUID otherCustomerId;

    @BeforeEach
    void setUp() {
        customerId      = UUID.randomUUID();
        staffId         = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();

        customerToken      = jwtProvider.generateToken(customerId,      "customer@test.com",  UserRole.CUSTOMER);
        staffToken         = jwtProvider.generateToken(staffId,         "staff@skylanka.lk",  UserRole.AIRLINE_OPERATIONS_OFFICER);
        otherCustomerToken = jwtProvider.generateToken(otherCustomerId, "other@test.com",     UserRole.CUSTOMER);
    }

    // =========================================================================
    // Helper: create a confirmed booking + PAID payment in DB
    // =========================================================================

    private UUID createPaidBooking() {
        // Minimal stub entities — in a real multi-module project these come from sibling modules
        User user = new User();
        user.setId(customerId);
        user.setEmail("customer@test.com");
        user.setFirstName("Kasun");
        user.setLastName("Perera");
        user.setRole(UserRole.CUSTOMER);
        user.setActive(true);

        Flight flight = new Flight();
        flight.setFlightNumber("UL201-" + UUID.randomUUID().toString().substring(0, 4));
        flight.setOriginCode("CMB");
        flight.setOriginName("Colombo");
        flight.setDestinationCode("LHR");
        flight.setDestinationName("London");
        flight.setDepartureTime(LocalDateTime.now().plusDays(5));
        flight.setArrivalTime(LocalDateTime.now().plusDays(5).plusHours(11));

        Seat seat = new Seat();
        seat.setSeatNumber("12A");
        seat.setCabinClass("ECONOMY");

        Passenger passenger = new Passenger();
        passenger.setFirstName("Kasun");
        passenger.setLastName("Perera");
        passenger.setPassportNumber("N1234567");

        Booking booking = new Booking();
        booking.setId(customerId); // reuse for simplicity
        booking.setBookingReference("SKL-TEST-" + UUID.randomUUID().toString().substring(0, 6));
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setUser(user);
        booking.setFlight(flight);
        booking.setSeat(seat);
        booking.setPassenger(passenger);

        Booking saved = bookingRepo.save(booking);

        Payment payment = new Payment();
        payment.setBooking(saved);
        payment.setAmount(BigDecimal.valueOf(250.00));
        payment.setCurrency("USD");
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setTransactionReference("TXN-" + UUID.randomUUID());
        payment.setPaidAt(LocalDateTime.now());
        paymentRepo.save(payment);

        return saved.getId();
    }

    // =========================================================================
    // TC-01: Generate ticket — success
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("INT-01: Staff can generate ticket for PAID booking")
    void staffGeneratesTicket() throws Exception {
        bookingId = createPaidBooking();

        GenerateTicketRequest req = new GenerateTicketRequest();
        req.setBookingId(bookingId.toString());

        MvcResult result = mockMvc.perform(post("/api/tickets/generate")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ticketStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.ticketVersion").value(1))
                .andExpect(jsonPath("$.data.ticketNumber").value(startsWith("SKL-")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        ticketId = UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
        qrToken  = objectMapper.readTree(body).path("data").path("qrCodeToken").asText();
    }

    // =========================================================================
    // TC-02: Reject generation for PENDING payment
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("INT-02: Reject ticket generation — no PAID payment")
    void rejectWhenNoPaidPayment() throws Exception {
        // Booking with no payment at all
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("u2@test.com"); user.setFirstName("A"); user.setLastName("B");
        user.setRole(UserRole.CUSTOMER); user.setActive(true);

        Flight f = new Flight();
        f.setFlightNumber("UL999-" + UUID.randomUUID().toString().substring(0,4));
        f.setOriginCode("CMB"); f.setDestinationCode("SIN");
        f.setDepartureTime(LocalDateTime.now().plusDays(3));
        f.setArrivalTime(LocalDateTime.now().plusDays(3).plusHours(4));

        Seat s = new Seat(); s.setSeatNumber("5B"); s.setCabinClass("ECONOMY");
        Passenger p = new Passenger(); p.setFirstName("A"); p.setLastName("B");

        Booking noPaidBooking = new Booking();
        noPaidBooking.setBookingReference("SKL-NP-" + UUID.randomUUID().toString().substring(0,6));
        noPaidBooking.setBookingStatus(BookingStatus.PENDING);
        noPaidBooking.setUser(user); noPaidBooking.setFlight(f);
        noPaidBooking.setSeat(s);   noPaidBooking.setPassenger(p);
        Booking saved = bookingRepo.save(noPaidBooking);

        GenerateTicketRequest req = new GenerateTicketRequest();
        req.setBookingId(saved.getId().toString());

        mockMvc.perform(post("/api/tickets/generate")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_COMPLETED"));
    }

    // =========================================================================
    // TC-04: Prevent duplicate ticket generation
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("INT-04: Prevent duplicate ticket for same booking")
    void preventDuplicateTicket() throws Exception {
        Assumptions.assumeTrue(bookingId != null, "Depends on INT-01");

        GenerateTicketRequest req = new GenerateTicketRequest();
        req.setBookingId(bookingId.toString());

        mockMvc.perform(post("/api/tickets/generate")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TICKET_ALREADY_EXISTS"));
    }

    // =========================================================================
    // TC-05: Customer gets own ticket
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("INT-05: Customer retrieves their own ticket")
    void customerGetsOwnTicket() throws Exception {
        Assumptions.assumeTrue(ticketId != null, "Depends on INT-01");

        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ticketId.toString()));
    }

    // =========================================================================
    // TC-06: Customer cannot get another customer's ticket (IDOR)
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("INT-06: Customer cannot access another customer's ticket (IDOR)")
    void customerCannotGetOtherTicket() throws Exception {
        Assumptions.assumeTrue(ticketId != null, "Depends on INT-01");

        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // TC-07: Validate ACTIVE ticket via QR
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("INT-07: QR token validates ACTIVE ticket")
    void validateActiveTicket() throws Exception {
        Assumptions.assumeTrue(qrToken != null, "Depends on INT-01");

        ValidateTicketRequest req = new ValidateTicketRequest();
        req.setQrCodeToken(qrToken);

        mockMvc.perform(post("/api/tickets/validate")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.ticketStatus").value("ACTIVE"));
    }

    // =========================================================================
    // TC-14: Customer cannot reissue (role check)
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("INT-14: CUSTOMER role is forbidden from reissuing")
    void customerCannotReissue() throws Exception {
        Assumptions.assumeTrue(ticketId != null, "Depends on INT-01");

        ReissueTicketRequest req = new ReissueTicketRequest();
        req.setReason("Attempt by customer");

        mockMvc.perform(post("/api/tickets/" + ticketId + "/reissue")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // TC-10 + TC-11: Reissue creates new version, old marked REISSUED
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("INT-10+11: Reissue creates new ticket version, old marked REISSUED")
    void reissueCreatesNewVersion() throws Exception {
        Assumptions.assumeTrue(ticketId != null, "Depends on INT-01");

        ReissueTicketRequest req = new ReissueTicketRequest();
        req.setReason("Flight rescheduled");

        MvcResult result = mockMvc.perform(post("/api/tickets/" + ticketId + "/reissue")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketVersion").value(2))
                .andExpect(jsonPath("$.data.ticketStatus").value("ACTIVE"))
                .andReturn();

        String body    = result.getResponse().getContentAsString();
        String newToken = objectMapper.readTree(body).path("data").path("qrCodeToken").asText();
        UUID   newId    = UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());

        // Old QR token should no longer validate as ACTIVE
        ValidateTicketRequest vReq = new ValidateTicketRequest();
        vReq.setQrCodeToken(qrToken); // old token

        mockMvc.perform(post("/api/tickets/validate")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false)); // old QR invalid

        // New QR token validates
        ValidateTicketRequest vReq2 = new ValidateTicketRequest();
        vReq2.setQrCodeToken(newToken);

        mockMvc.perform(post("/api/tickets/validate")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vReq2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));

        // Update for subsequent tests
        ticketId = newId;
        qrToken  = newToken;
    }

    // =========================================================================
    // TC-15: Customer cannot void (role check)
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("INT-15: CUSTOMER role is forbidden from voiding")
    void customerCannotVoid() throws Exception {
        Assumptions.assumeTrue(ticketId != null, "Depends on INT-08");

        VoidTicketRequest req = new VoidTicketRequest();
        req.setReason("Attempt by customer");

        mockMvc.perform(post("/api/tickets/" + ticketId + "/void")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // TC-13: No-show void by staff
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("INT-13: Staff can void ticket for no-show")
    void staffVoidsNoShow() throws Exception {
        Assumptions.assumeTrue(ticketId != null, "Depends on INT-08");

        VoidTicketRequest req = new VoidTicketRequest();
        req.setReason("Passenger no-show");

        mockMvc.perform(post("/api/tickets/" + ticketId + "/void")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketStatus").value("VOID"))
                .andExpect(jsonPath("$.data.voidReason").value("Passenger no-show"));
    }

    // =========================================================================
    // TC-08: Voided ticket QR fails validation
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("INT-08: QR token fails validation for VOID ticket")
    void voidedTicketQrFails() throws Exception {
        Assumptions.assumeTrue(qrToken != null, "Depends on previous tests");

        ValidateTicketRequest req = new ValidateTicketRequest();
        req.setQrCodeToken(qrToken);

        mockMvc.perform(post("/api/tickets/validate")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false));
    }

    // =========================================================================
    // TC-16: PDF download works
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("INT-16: PDF download endpoint returns application/pdf")
    void pdfDownloadWorks() throws Exception {
        // Create a fresh paid booking + ticket for this test
        UUID freshBookingId = createPaidBooking();
        GenerateTicketRequest gReq = new GenerateTicketRequest();
        gReq.setBookingId(freshBookingId.toString());

        MvcResult genResult = mockMvc.perform(post("/api/tickets/generate")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gReq)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID freshTicketId = UUID.fromString(
                objectMapper.readTree(genResult.getResponse().getContentAsString())
                        .path("data").path("id").asText());

        mockMvc.perform(get("/api/tickets/" + freshTicketId + "/pdf")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    // =========================================================================
    // TC-400: Unauthenticated request is rejected
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("INT-AUTH: Unauthenticated request returns 401")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/tickets/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // TC-BOOKING: Get tickets by booking ID
    // =========================================================================

    @Test
    @Order(14)
    @DisplayName("INT-BOOKING: Get all ticket versions by booking ID")
    void getTicketsByBooking() throws Exception {
        Assumptions.assumeTrue(bookingId != null, "Depends on INT-01");

        mockMvc.perform(get("/api/tickets/booking/" + bookingId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
    }
}
