# SkyLanka-Air integration and UAT checklist

These checks are designed for the existing local SQL Server database. They do not add H2, change the server port, or change the application's local `LocalDate`/`LocalDateTime` behavior.

## Automated checks

Run unit and contract tests with Maven:

```text
mvn test
```

Run the live smoke suite while the app is running on the existing local server:

```text
./scripts/uat-smoke.sh
```

The smoke suite covers public pages, unauthenticated redirect behavior, API login/JWT validation, protected API access, and browser-session login.

## End-to-end acceptance cases

1. **Customer booking**
   - Log in as `demo@skylankaair.com / demo123`.
   - Search a future flight and open it.
   - Confirm unavailable seats are disabled, select an available seat, and verify the selected seat/class summary.
   - Submit passenger details and confirm the booking is `PENDING`.
2. **Mock payment**
   - On the booking page, verify the sandbox notice is visible.
   - Pay using Visa, Mastercard, and Bank Transfer one at a time on separate pending bookings.
   - Confirm the transaction starts with `MOCK-TX-`, the booking becomes `CONFIRMED`, and a ticket is issued.
   - Confirm no real payment provider is contacted.
3. **Email notifications**
   - Leave email disabled for local development and verify in-app notifications still appear.
   - In an SMTP test environment, set `NOTIFICATIONS_EMAIL_ENABLED=true` and `spring.mail.*`; create and pay for a booking and verify both messages arrive.
4. **Authorization**
   - Verify a logged-out user is redirected from `/dashboard`.
   - Verify customer access cannot open finance, operations, support, or admin screens.
   - Verify `/api/me` rejects missing/invalid JWTs and accepts the token from `/api/auth/login`.
5. **Audit coverage**
   - Perform login, booking, payment, cancellation, role/user, and seat-management actions.
   - As admin, verify `HTTP_REQUEST` entries plus the existing business action entries appear newest-first in the audit table.
6. **Operations**
   - As operations, block an available seat and verify customers cannot select it.
   - Cancel a flight and verify active bookings are cancelled and seats are released according to the existing business rules.
7. **Dashboard**
   - Verify upcoming, confirmed, pending, paid-to-date, and unread notification metrics match the visible booking/notification records.

8. **Ticket management**
   - As operations or admin, open `/operations/tickets` and verify the total/issued/reissued/void counters match the table.
   - Search by ticket number, passenger and booking reference; apply each status filter.
   - Download a ticket PDF and confirm the QR code is visible in the upper-right corner.
   - Reissue an issued ticket: the old one becomes `REISSUED`, a new `ISSUED` ticket appears. Void a live ticket and confirm it becomes `VOID`.
   - Confirm a customer account gets an access-denied response on `/operations/tickets`.

9. **Flight and fleet management**
   - Search with a past date or identical origin and destination: a clear message is shown and no search runs.
   - Search a route/date with no flights: suggestions for other dates and destinations appear.
   - On Fleet & routes, change an aircraft's seats per row and business seats; create a flight on it and confirm the seat map matches, and that existing flights are unchanged.
   - On the Operations page, assign and remove an operations officer on a flight and confirm both actions appear in the audit log.

10. **Notifications and payment gateways (design patterns)**
   - Create a booking: a "Booking Created" notification appears for the customer (and an email when email is enabled).
   - Pay by card: approved with a `MOCK-TX-` reference, ticket issued, "Payment Successful" notification. Pay by bank transfer: approved with a `BANK-TX-` reference.
   - Pay with card `4000 0000 0000 0002`: declined, a FAILED attempt is kept, "Payment Declined" notification, and a retry works.
   - Cancel, modify and let a booking expire: the matching notification appears each time.
   - With email enabled but the mail server unreachable, booking and payment still succeed (the failure is only logged).
   - The application log shows `[event] ...` lines and `payment attempt ...` lines in the `payment-audit` logger.

11. **Admin dashboard previews**
   - On `/admin`, the audit log shows only the five newest entries and Manage users only the first five users, each with a "Showing X of Y" footer.
   - "View full audit log" opens `/admin/audit` (50 entries per page, newest first, Newer/Older links, flagged entries highlighted).
   - "View all users" opens `/admin/users` with every account. Toggle and Set role there return to the same page, and the role dropdown shows each user's current role.
   - A non-admin account gets an access-denied response on `/admin/audit` and `/admin/users`.

## Database and environment guardrails

- Datasource remains Microsoft SQL Server (`mssql-jdbc`) and uses the `DB_USERNAME` and `DB_PASSWORD` overrides (set `SPRING_DATASOURCE_URL` to point at a different server).
- `server.port=8080` remains unchanged.
- No H2 dependency or in-memory datasource was added.
- The existing local date/time calls remain unchanged; no timezone conversion was introduced.