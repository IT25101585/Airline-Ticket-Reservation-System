# SkyLanka-Air

**Online Airline Ticket Reservation System** — a full-stack web application for searching flights, booking seats, paying, and managing tickets, with dedicated portals for airline staff.

Built with Spring Boot, Thymeleaf and Microsoft SQL Server.

---

## Table of contents

1. [Features](#features)
2. [Tech stack](#tech-stack)
3. [Getting started](#getting-started)
4. [Configuration](#configuration)
5. [Demo accounts and roles](#demo-accounts-and-roles)
6. [Design patterns](#design-patterns)
7. [Business rules](#business-rules)
8. [REST API](#rest-api)
9. [Testing](#testing)
10. [Project structure](#project-structure)
11. [Security notes](#security-notes)
12. [Documentation](#documentation)
13. [Troubleshooting](#troubleshooting)

---

## Features

**Customers**
- Search flights by origin, destination and date, with input validation (no malformed or past dates, origin and destination must differ) and "try adjusting your search" suggestions (other dates on the route, other destinations from the origin) when nothing matches
- Interactive seat map with live availability; selected seats are held temporarily and released on change or timeout
- Guest checkout (no account needed) or registered accounts with a dashboard, profile and notifications
- Sandbox payment with retry on decline
- QR-coded PDF e-ticket, itemised payment receipt and booking modification with ticket reissue
- Online check-in, cancellation with policy-based refunds, support requests with reference numbers
- Loyalty points (1 point per LKR 100 paid) and marketing opt-in

**Staff portals** (role-based)
- **Operations** — flights, assigning operations officers to flights (many-to-many), seat blocking, passenger manifest, check-in, no-shows, fleet and routes, configurable aircraft cabin layouts, and a dedicated **Ticket Management** dashboard (search, status counters, QR validation lookup, PDF download, reissue/void)
- **Finance** — transactions, invoices (PDF), refund-request approval, reports
- **Support** — complaint tracking (Under Review / In Progress / Resolved)
- **Marketing** — promotions, campaigns and newsletters (opted-in customers only)
- **Admin** — users and roles, system settings (hold times, tax %, service fee %), audit log. The dashboard previews the five newest audit entries and the first five users; *View full audit log* (`/admin/audit`, paged, newest first) and *View all users* (`/admin/users`) open the complete lists

**Platform**
- Session login for the browser UI, signed JWT for `/api/**`
- Request-level and business audit logging, with flagging of unusual administrative activity
- Optional SMTP email notifications
- Pluggable payment gateway (a mock sandbox gateway is included)

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 17 |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Validation, Mail) |
| Templates / UI | Thymeleaf, Bootstrap 5, vanilla JavaScript |
| Database | Microsoft SQL Server (`mssql-jdbc`) |
| PDF generation | Apache PDFBox 3.0.5 (QR codes use an in-house encoder, no extra dependency) |
| Build | Maven |
| Tests | JUnit 5 and Spring Boot Test |

## Getting started

### Prerequisites

- JDK 17+
- Maven 3.9+
- Microsoft SQL Server (2019 or later recommended) listening on `localhost:1433`, with SQL authentication enabled
- SSMS or `sqlcmd` to run the database script

### 1. Create the database

Run **`database/skylanka-air.sql`** against your SQL Server instance. The script has two modes, selected by one line near the top of the file:

| Mode | When to use | Effect |
|---|---|---|
| `FRESH` (default) | New machine, or resetting a demo | Creates the `AirlineReservation` database if needed, **drops** all application tables and recreates the full schema |
| `UPGRADE` | You have an older database whose data you must keep | Keeps data; relaxes the old one-payment-per-booking and one-ticket-per-passenger constraints and allows the `REISSUED` ticket status |

> ⚠️ `FRESH` deletes existing data in the application tables. Never run it against a database you need.

Demo users and flights are not in the script; the application seeds them on first start when the tables are empty.

### 2. Configure credentials

The `local` profile is active by default and reads the database login from `application-local.properties` (development values only). Override them with environment variables if your SQL Server login differs:

```bash
export DB_USERNAME=sa
export DB_PASSWORD=your_password
```

### 3. Run the application

```bash
mvn spring-boot:run
```

or run `SkyLankaAirApplication` from your IDE. Then open **http://localhost:8080/**.

### 4. Build a jar (optional)

```bash
mvn clean package
java -jar target/skylanka-air-1.0.0.jar
```

## Configuration

| Setting | Environment variable | Default | Description |
|---|---|---|---|
| Database user | `DB_USERNAME` | `sa` (local profile) | SQL Server login |
| Database password | `DB_PASSWORD` | development value (local profile) | SQL Server password |
| Database URL | `SPRING_DATASOURCE_URL` | `jdbc:sqlserver://localhost:1433;databaseName=AirlineReservation;...` | Point at another server |
| JWT secret | `JWT_SECRET` | development value (local profile) | Signing key for API tokens |
| JWT lifetime | `JWT_TTL_MINUTES` | `60` | Token validity |
| Payment gateway | `PAYMENT_GATEWAY` | `mock` | Payment provider selection |
| Email | `NOTIFICATIONS_EMAIL_ENABLED` | `false` | Set `true` and configure `spring.mail.*` to send real email |
| Email sender | `NOTIFICATIONS_EMAIL_FROM` | `no-reply@skylankaair.local` | From address |
| Unpaid booking hold | `booking.hold-minutes` | `15` | Minutes before a `PENDING` booking is released |

Seat-hold time, tax % and service-fee % are managed at runtime from the Admin page.

**Non-local profiles** (staging, production) must supply non-default `DB_PASSWORD` and `JWT_SECRET`; startup is refused if they are blank or still the development values.

## Demo accounts and roles

| Role | Email | Password | Access |
|---|---|---|---|
| Customer | demo@skylankaair.com | demo123 | Booking, dashboard, profile |
| Operations | ops@skylankaair.com | ops123 | Flights, seats, manifest, check-in, fleet |
| Finance | finance@skylankaair.com | finance123 | Transactions, invoices, refunds, reports |
| Support | support@skylankaair.com | support123 | Complaints |
| Marketing | marketing@skylankaair.com | marketing123 | Promotions, campaigns |
| Admin | admin@skylankaair.com | admin123 | Users, settings, audit log |

These are development credentials seeded for demos. Change or remove them before any real deployment.

## Flight and fleet management

- **Search validation:** `/search` rejects malformed dates, past dates (customers only) and identical origin/destination, both on the server and in `route-search.js`. Empty customer searches show suggestions built by `FlightService.hints`.
- **Aircraft layouts:** each aircraft has *seats per row* (2-10) and *business seats*. Operations edits them on **Fleet & routes** (`POST /operations/fleet/aircraft/{id}/layout`, audited). New flights whose aircraft matches a fleet tail number or model use that layout; existing flights and their bookings are never changed. Defaults are 6 per row and 6 business seats.
- **Officer assignment:** Operations and Admin assign or remove operations officers per flight on the Operations page (audited). The officer who creates a flight is assigned automatically. Stored in the `flight_operations_officers` join table.

## Ticket management and e-tickets

Operations and Admin staff manage tickets at **`/operations/tickets`** (sidebar: *Tickets*).

- Counters for total, issued, reissued and voided tickets
- Search by ticket number, passenger, booking reference (PNR), flight or route, plus status filters
- Ticket detail view with reissue history and the QR validation value
- Download the live e-ticket PDF
- **Reissue** (old ticket becomes `REISSUED`, a new live ticket points back to it) and **Void** (ticket becomes invalid)
- Ticket / QR lookup against the current database records

The e-ticket PDF always shows a scannable QR code in the upper-right corner. Its value is `BOOKING_REFERENCE|PASSENGER_ID|TICKET_NUMBER`, and tickets that predate QR support get a value assigned when their PDF is next downloaded. No database migration is needed for these features.

Suggested demo flow: sign in as Operations, open *Tickets*, search and open an issued ticket, download the PDF and show the QR, reissue it, void the reissued ticket, then validate it in the lookup.

## Design patterns

Four GoF patterns are applied in the booking and payment flow (details, file references and benefits in [`docs/DESIGN-PATTERNS.md`](docs/DESIGN-PATTERNS.md)):

| Pattern | Where |
|---|---|
| **Observer** | `shared/event/`: booking and payment services publish events; in-app, email and log observers react independently |
| **Strategy** | `PaymentGateway` interface with `MockPaymentGateway` (cards) and `BankTransferGateway` |
| **Factory** | `PaymentGatewayFactory` picks the gateway for the chosen payment method |
| **Decorator** | `AuditingPaymentGateway` adds payment-attempt auditing around any gateway |

## Business rules

- **Payment sandbox:** no real provider is contacted. Card `4000 0000 0000 0002` is always declined (to demonstrate retry); any other valid-looking card is approved. Card payments get `MOCK-TX-` references and bank transfers `BANK-TX-` references.
- **Refunds:** flights cancelled by the airline are fully refundable. Customer cancellations are refunded in full, half, or not at all depending on the 48-hour and 24-hour thresholds before departure. The policy is shown before payment.
- **Money:** all fare, payment and refund amounts use `BigDecimal` with two-decimal rounding (LKR).
- **Seat holds:** at most 9 held seats per visitor, 9 passengers per booking, and 30 hold requests per minute per client address.
- **Tickets:** modifying a confirmed booking marks the old ticket `REISSUED` and issues a new one; a no-show voids the ticket.
- **Passports** are required only for international flights.
- **Finance data** (`/finance/**`, refund approval) is restricted to the `FINANCE` role.

## REST API

JSON endpoints under `/api` use bearer-token authentication (CSRF is disabled for this path only).

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Returns `accessToken` for valid credentials |
| GET | `/api/me` | Bearer | Current user |
| GET | `/api/bookings` | Bearer | List the caller's bookings |
| GET | `/api/bookings/{ref}` | Bearer | Booking details |
| POST | `/api/bookings` | Bearer | Create a booking |
| POST | `/api/bookings/{ref}/cancel` | Bearer | Cancel a booking |
| GET | `/api/notifications/unread-count` | Session | Unread notification count |

Example:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@skylankaair.com","password":"demo123"}' | jq -r .accessToken)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/me
```

## Testing

```bash
mvn test                      # unit and contract tests
./scripts/uat-smoke.sh        # live smoke test against a running app (needs curl and jq)
```

The manual acceptance cases (booking, payment, email, authorization, audit, operations, dashboard) are in [`docs/UAT-CHECKLIST.md`](docs/UAT-CHECKLIST.md).

## Project structure

```text
SkyLanka-Air/
├── database/
│   └── skylanka-air.sql          # schema: fresh install or upgrade
├── docs/
│   ├── DesignAlignmentChanges.md
│   └── UAT-CHECKLIST.md
├── scripts/
│   └── uat-smoke.sh
├── src/
│   ├── main/java/com/skylanka/air/
│   │   ├── user/                # accounts, profile, loyalty, password reset
│   │   ├── flight/              # flights, airports, search hints, officer assignment
│   │   ├── seat/                # seat map, aircraft-based layouts, holds, rate limiting
│   │   ├── booking/             # bookings, passengers, expiry scheduler
│   │   ├── payment/             # payments, refunds, gateway strategies/factory/decorator
│   │   ├── ticket/              # e-tickets, reissue, void
│   │   ├── shared/              # security, config, audit, check-in,
│   │   │                        # complaints, notifications, fleet,
│   │   │                        # event/ (Observer), pdf/ (ticket, receipt, invoice, QR)
│   │   └── SkyLankaAirApplication.java
│   ├── main/resources/
│   │   ├── templates/           # Thymeleaf views
│   │   ├── static/              # css, js, images
│   │   └── application*.properties
│   └── test/java/               # unit and contract tests
├── pom.xml
└── README.md
```

The six core subsystems (User, Flight, Seat, Booking, Payment, Ticket) each have their own `entity`, `repository`, `service` and `controller` packages. Cross-cutting features live in `shared`.

## Security notes

- Session id is rotated on login, registration and guest sign-in (session-fixation protection); cookies are `HttpOnly` and `SameSite=Lax`.
- CSRF protection stays on for browser forms.
- Passwords are stored with BCrypt.
- `application-local.properties` holds development-only credentials. Do not reuse them elsewhere.
- The seat-hold rate limiter is in-memory, so it applies per instance.

## Documentation

- [`docs/DesignAlignmentChanges.md`](docs/DesignAlignmentChanges.md) — how the implementation maps to the submitted design document (including Flight Management and fleet changes), and what was deliberately left out
- [`docs/DESIGN-PATTERNS.md`](docs/DESIGN-PATTERNS.md) — the four design patterns, where they are and why
- [`docs/UAT-CHECKLIST.md`](docs/UAT-CHECKLIST.md) — automated checks and acceptance cases

## Troubleshooting

| Problem | Fix |
|---|---|
| `Login failed for user` at startup | Check `DB_USERNAME` / `DB_PASSWORD`, and that SQL Server accepts SQL authentication |
| Connection refused on 1433 | Enable TCP/IP in SQL Server Configuration Manager and restart the service |
| `Cannot open database "AirlineReservation"` | Run `database/skylanka-air.sql` first |
| Payment retry, ticket reissue, aircraft layouts or officer assignment fail on an older database | Run `database/skylanka-air.sql` in `UPGRADE` mode (Spring `ddl-auto=update` also adds the new columns and table on start) |
| Old `@skylinkair.com` demo logins no longer work | Use the `@skylankaair.com` accounts; the app renames the old demo accounts on start |
| Startup refused about default secrets | You are on a non-local profile; set `DB_PASSWORD` and `JWT_SECRET` |
| Ticket PDF or new pages look outdated after pulling changes | Stop the app, run `mvn clean compile` (or *Build > Rebuild Project* in IntelliJ), restart, and download the PDF again |
| Port 8080 already in use | Set `server.port` (or `SERVER_PORT`) to a free port |
