# Design-document alignment changes

The submitted design document (B6G1-03) could not be changed, so the application was brought in line with it.
Each row: what the document says -> what the project now does.

| Document | Implementation |
|---|---|
| Guest checkout (Ethics §5, Guest Customer actor) | `/book/{id}` is public. Guests enter name/email/contact; a guest account is created, the guest is signed in for that session and can pay and download the ticket. Guest emails can be claimed later through *Forgot password*. |
| Delete Account (activity 4.1) | Profile -> *Delete account* (password required). Personal data is erased and the account deactivated; booking/payment rows are kept. Blocked while confirmed upcoming bookings exist or for the last admin. |
| Seat is temporarily blocked, released on change/timeout (activity 4.3, `Seat.lockSeat`) | Selecting a seat calls `POST /seats/{id}/hold` (status `HELD`, timeout from *System settings*, default 10 min); unticking releases it; a scheduler frees lapsed holds; the seat map polls `/flights/{id}/seat-status` and tells the customer when a seat was just taken. |
| Payment declined -> retry, cancel returns to summary (activity 4.5) | Sandbox card `4000 0000 0000 0002` is declined: the attempt is stored as `FAILED`, the booking stays `PENDING`, the customer can retry. Each attempt is its own `Payment` row (booking receives 1..* payments). *Cancel payment* returns to the summary. |
| QR-coded e-ticket (4.6) | The ticket PDF contains a real QR code (in-house encoder `shared/pdf/QrCode`, no new dependency). |
| Ticket reissue on modification; `reissuedFrom` (class diagram) | Modifying a confirmed booking/passenger marks the old ticket `REISSUED` and issues a new one pointing at it. Operations can also *Reissue* / *Void* a ticket from the flight manifest. |
| Void ticket on no-show (4.6) | Marking a no-show voids that passenger's ticket. |
| Complaint reference number; statuses Under Review / In Progress / Resolved | Each complaint gets `CMP-XXXXXXXX`; customers see a *My support requests* tracker; status labels follow the document. |
| Marketing newsletters are opt-in | `User.marketingOptIn` (register form + profile). Marketing broadcasts only reach opted-in customers. |
| System flags unusual administrative activity | `AuditService` writes a highlighted `FLAGGED_ACTIVITY` entry when ADMIN privilege is granted or a staff user performs 15+ audited actions within 5 minutes. |
| Transaction and refund records restricted to Finance | `/finance/**` and refund-request approval are `FINANCE` only (admin no longer has them). |
| Passport only for international flights | `Flight.international` is derived from the airports' countries; passport is optional on domestic flights. |
| Refund policy shown before confirming | Shown (with acknowledgement) on the booking page and next to the payment form. |
| Airport class | `Airport` entity (code, city, name, country); `Flight.departureAirport` / `arrivalAirport` are linked automatically from origin/destination. |
| Customer: passportNumber, loyaltyPoints | Stored on the user; loyalty = 1 point per LKR 100 paid, reversed on refund/void. |
| Staff: employeeId, department | Assigned automatically to staff accounts. |
| Generate Invoices (Finance) | Finance -> transactions -> *Invoice* (PDF). |
| Configure System Settings (Admin) | Admin page: unpaid-booking hold, seat hold, tax %, service-fee %. |
| Support/Finance role naming | Documented in the code as `SUPPORT`, `FINANCE`, ... ; brand text is *SkyLanka Air*. |

## Not implemented (deliberately)
* Role-specific attributes `specialization`, `shiftSchedule`, `approvalLimit`, `campaignQuota`, `accessLevel` - they have no behaviour in the document, so they would be dead columns.
* Separate `Customer` / `Staff` / `Refund` classes - the project keeps a single `User` with a `Role`, and refunds are `Payment.refundAmount` plus `RefundRequest` (dispute) records.
* "Modifies aircraft layout mappings" - fleet screens still add aircraft/routes and change aircraft status only.

## Upgrading an existing database
Fresh install: run `database/skylanka-air.sql` (mode `FRESH`, the default).
Existing database: set the mode at the top of `database/skylanka-air.sql` to `'UPGRADE'` and run it once **before** starting the app (drops the old one-payment-per-booking / one-ticket-per-passenger unique constraints and allows the `REISSUED` ticket status); Spring `ddl-auto=update` adds the new columns and tables.

## Flight Management alignment

| Design item | Change |
|---|---|
| Search validation | `/search` now rejects malformed dates, past dates (customers) and identical origin/destination, with an on-page message. The same checks run in `route-search.js` before submit. |
| "Try adjusting your search" | Empty customer searches list concrete suggestions: other departure dates on the same route, other destinations from the chosen origin, and a clear-filters link (`FlightService.hints`). |
| Modify aircraft layout mappings | Aircraft carry `seatsPerRow` and `businessSeats`. Operations edits them on **Fleet & routes** (`POST /operations/fleet/aircraft/{id}/layout`, audited). New flights whose aircraft field matches a fleet tail number or model use that layout; existing flights and their bookings are not changed. The customer seat map positions the aisle from the row width. |
| Operations officers ↔ flights (many-to-many) | New join table `flight_operations_officers`. Officers can be assigned/removed per flight on the Operations page (audited); the creating officer is assigned automatically. |

Database: `ddl-auto=update` adds the new columns/table on start. To apply them with the script instead, run `database/skylanka-air.sql` in `UPGRADE` mode.

Brand: the product, packages (`com.skylanka.air`), artifact (`skylanka-air`), SQL script and demo accounts (`*@skylankaair.com`) are all SkyLanka Air. On start the app renames legacy `*@skylinkair.com` demo accounts and the old airline name on existing flights, so existing databases keep working.

## Design patterns (implementation structure)

Observer (event publisher with in-app, email and log observers), Strategy (card and bank-transfer gateways), Factory (gateway chosen by payment method) and Decorator (payment auditing) were added to the booking and payment flow. They are internal structure only: the use cases, activity flows and class-diagram classes are unchanged. `Notification.send()` is carried out by the observers and `Payment.processPayment()` by `PaymentService.pay(...)` using the selected gateway. See `docs/DESIGN-PATTERNS.md`.

## Admin dashboard layout

The System Administrator's "Manage Audit Logs" and "Manage User Accounts" use cases are unchanged. The dashboard now previews the five newest audit entries and the first five users to keep it short, with separate full pages (`/admin/audit`, `/admin/users`) for the complete lists.
