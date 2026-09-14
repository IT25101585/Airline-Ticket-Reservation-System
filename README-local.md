# SkyLanka Air Travels — Ticket Management Backend

**Spring Boot 3 · Java 17 · SQL Server 2025 · JWT · iText7 · ZXing**

---

## Quick Start

### 1. Configure the database

Open `src/main/resources/application.properties` and set your SQL Server `sa` password:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=SkyLankaAirTicketDB;encrypt=true;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=YOUR_PASSWORD_HERE
```

The database `SkyLankaAirTicketDB` has already been created. Hibernate will auto-create all tables on first startup (`ddl-auto=update`).

### 2. Download Maven Wrapper

The first run of `mvnw.cmd` will automatically download Maven 3.9.9. Requires internet access.

### 3. Run the application

```cmd
cd "c:\Users\Silukshan\Desktop\ticket management"
mvnw.cmd spring-boot:run
```

Server starts at **http://localhost:8080**

### 4. Explore the API

Open **http://localhost:8080/swagger-ui.html** — full interactive Swagger UI with all endpoints.

---

## API Endpoints

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| `POST` | `/api/tickets/generate` | AIRLINE_OPS, ADMIN | Generate ticket after payment |
| `GET`  | `/api/tickets/my-tickets` | CUSTOMER | Get customer's own tickets |
| `GET`  | `/api/tickets/{id}` | ALL | Get ticket by ID |
| `GET`  | `/api/tickets/booking/{bookingId}` | ALL | Get all ticket versions for a booking |
| `GET`  | `/api/tickets/{id}/pdf` | ALL (own) | Download e-ticket as PDF |
| `POST` | `/api/tickets/validate` | ALL | Validate QR code token |
| `POST` | `/api/tickets/{id}/reissue` | AIRLINE_OPS, ADMIN | Reissue ticket |
| `POST` | `/api/tickets/{id}/void` | AIRLINE_OPS, ADMIN | Void ticket |

---

## Authentication

All endpoints require a **JWT Bearer token**:

```
Authorization: Bearer <your_jwt_token>
```

Generate a token using `JwtTokenProvider.generateToken(userId, email, role)`.

---

## Running Tests

```cmd
mvnw.cmd test
```

Tests use an **H2 in-memory database** — no SQL Server connection required.

---

## Integration Flow

### Payment → Ticket (auto-generate)
```
Payment module calls:
  TicketService.generateAfterPayment(bookingId)
```

### Booking Modified → Reissue
```
Booking module calls:
  TicketService.reissueOnBookingModification(bookingId, reason)
```

### Booking Cancelled → Void
```
Booking module calls:
  TicketService.voidOnCancellation(bookingId, reason)
```

---

## Project Structure

```
src/main/java/com/skylanka/ticketmanagement/
├── config/          SecurityConfig, OpenApiConfig, AsyncConfig
├── controller/      TicketController
├── dto/             request & response DTOs
├── entity/          Ticket, TicketHistory, AuditLog
│   └── stubs/       User, Booking, Passenger, Flight, Seat, Payment (replace with teammates' real entities)
├── enums/           TicketStatus, TicketType, UserRole, BookingStatus, PaymentStatus, AuditAction
├── exception/       AppException, GlobalExceptionHandler, ErrorCode
├── repository/      TicketRepository, TicketHistoryRepository, AuditLogRepository
├── security/        JwtTokenProvider, JwtAuthenticationFilter, UserPrincipal
├── service/         TicketService, TicketNumberService, QRCodeService, PdfService,
│                    AuditLogService, NotificationService
└── util/            SecurityUtils
```

---

## Database Tables Created

| Table | Description |
|-------|-------------|
| `tickets` | Core ticket records — UNIQUE on ticket_number and qr_code_token |
| `ticket_histories` | Immutable status-change snapshots |
| `audit_logs` | Append-only audit trail |
| `users` | Stub — replace with User Management module |
| `bookings` | Stub — replace with Booking Management module |
| `passengers` | Stub — replace with Booking Management module |
| `flights` | Stub — replace with Flight Management module |
| `seats` | Stub — replace with Seat Management module |
| `payments` | Stub — replace with Payment Management module |

---

## Integrating With Teammates' Modules

1. Delete the files in `entity/stubs/`
2. Add your teammate's module as a Maven dependency (or merge into a multi-module project)
3. Update import statements in `Ticket.java`, `TicketHistory.java`, and `TicketService.java`
4. Make sure column names match — especially `booking_id`, `user_id`, `flight_id`, `seat_id`, `passenger_id`, `payment_id`

---

*SkyLanka Air Travels — University Software Engineering Project*
