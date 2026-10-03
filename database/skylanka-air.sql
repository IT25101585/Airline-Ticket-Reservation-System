/*
    ============================================================
    SkyLanka-Air - Database Script (SQL Server)
    ============================================================

    Database : AirlineReservation
    Usage    : run this ONE file in SSMS (or sqlcmd) - choose the mode below.

    MODE 'FRESH'   (default)
        Creates the database if needed, DROPS every application table
        and recreates the full schema (keys, constraints, indexes).
        Use for a new machine or a demo reset.
        WARNING: all existing data in these tables is lost.

    MODE 'UPGRADE'
        Keeps your data. Upgrades a database created by an older build:
          - payments   : allow several payment attempts per booking
          - tickets    : allow several tickets per passenger (reissue)
          - tickets    : status now accepts 'REISSUED'
        Spring's ddl-auto=update then adds any new columns/tables when
        the application starts. Safe to re-run.

    Demo accounts and flights are NOT created here; the application
    seeds them on first start when the tables are empty.
    ============================================================
*/

/* ---- CHOOSE THE MODE: 'FRESH' or 'UPGRADE' ---- */
IF OBJECT_ID('tempdb..##skylanka_mode') IS NOT NULL DROP TABLE ##skylanka_mode;
SELECT CAST('FRESH' AS VARCHAR(10)) AS mode INTO ##skylanka_mode;
GO

/* ============================================================
   1. CREATE DATABASE
   ============================================================ */

IF DB_ID(N'AirlineReservation') IS NULL
BEGIN
    CREATE DATABASE AirlineReservation;
END;
GO

USE AirlineReservation;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       2. DROP EXISTING TABLES
       ============================================================

       Tables are dropped in dependency order so that foreign keys
       do not prevent the operation.
       ============================================================ */

    DROP TABLE IF EXISTS tickets;
    DROP TABLE IF EXISTS check_ins;
    DROP TABLE IF EXISTS booking_passengers;
    DROP TABLE IF EXISTS refund_requests;
    DROP TABLE IF EXISTS payments;
    DROP TABLE IF EXISTS complaints;
    DROP TABLE IF EXISTS notifications;
    DROP TABLE IF EXISTS bookings;
    DROP TABLE IF EXISTS seats;
    DROP TABLE IF EXISTS flight_operations_officers;
    DROP TABLE IF EXISTS flights;
    DROP TABLE IF EXISTS airports;
    DROP TABLE IF EXISTS system_settings;
    DROP TABLE IF EXISTS aircraft;
    DROP TABLE IF EXISTS routes;
    DROP TABLE IF EXISTS promotions;
    DROP TABLE IF EXISTS campaigns;
    DROP TABLE IF EXISTS audit_logs;
    DROP TABLE IF EXISTS password_reset_tokens;
    DROP TABLE IF EXISTS customers;
    DROP TABLE IF EXISTS users;
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       3. USERS
       ============================================================ */

    CREATE TABLE users (
                           id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                           name NVARCHAR(100) NOT NULL,

                           email NVARCHAR(255) NOT NULL UNIQUE,

                           password NVARCHAR(255) NOT NULL,

                           contact_number NVARCHAR(30) NOT NULL,

                           role NVARCHAR(30) NOT NULL DEFAULT 'CUSTOMER',

                           active BIT NOT NULL DEFAULT 1,

                           created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

                           last_login DATETIME2 NULL,

                           passport_number NVARCHAR(20) NULL,

                           loyalty_points INT NOT NULL DEFAULT 0,

                           marketing_opt_in BIT NOT NULL DEFAULT 0,

                           is_guest BIT NOT NULL DEFAULT 0,

                           employee_id NVARCHAR(20) NULL,

                           department NVARCHAR(60) NULL,

                           CONSTRAINT CK_users_role
                               CHECK (role IN (
                                               'CUSTOMER',
                                               'OPERATIONS',
                                               'SUPPORT',
                                               'FINANCE',
                                               'MARKETING',
                                               'ADMIN'
                                   ))
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       3B. PASSWORD RESET TOKENS

       Single-use, time-limited tokens for the "forgot password" flow.
       A row is deleted once consumed or superseded by a newer request
       for the same user, so this table only ever holds live tokens.
       ============================================================ */

    CREATE TABLE password_reset_tokens (
                                            id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                                            user_id BIGINT NOT NULL,

                                            token NVARCHAR(64) NOT NULL UNIQUE,

                                            expires_at DATETIME2 NOT NULL,

                                            created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

                                            CONSTRAINT FK_password_reset_tokens_user
                                                FOREIGN KEY (user_id)
                                                    REFERENCES users(id)
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* (Former section 4, a standalone `customers` table, was removed: the application keeps
       customers in `users` with role = 'CUSTOMER' and no entity ever mapped that table.) */


    /* ============================================================
       4B. AIRCRAFT & ROUTES

       Standalone reference tables for Ops to maintain a fleet roster and route
       catalog. Neither is a foreign key on flights - that table keeps its own
       free-text origin/destination/aircraft columns so existing flights, seat
       generation, and fare logic are unaffected. These are a reference Ops can
       consult when creating a flight, not a hard requirement.
       ============================================================ */

    CREATE TABLE aircraft (
                               id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                               tail_number NVARCHAR(20) NOT NULL UNIQUE,

                               model NVARCHAR(100) NOT NULL,

                               seat_capacity INT NOT NULL,

                               status NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

                               seats_per_row INT NULL DEFAULT 6,

                               business_seats INT NULL DEFAULT 6,

                               CONSTRAINT CK_aircraft_status
                                   CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'RETIRED')),

                               CONSTRAINT CK_aircraft_capacity
                                   CHECK (seat_capacity > 0)
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    CREATE TABLE routes (
                             id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                             origin NVARCHAR(100) NOT NULL,

                             destination NVARCHAR(100) NOT NULL,

                             distance_km INT NULL,

                             standard_duration_minutes INT NULL
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       5. FLIGHTS
       ============================================================ */

    CREATE TABLE airports (
                              id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
                              airport_code NVARCHAR(5) NOT NULL UNIQUE,
                              city NVARCHAR(100) NOT NULL,
                              name NVARCHAR(150) NOT NULL,
                              country NVARCHAR(100) NOT NULL
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    CREATE TABLE system_settings (
                              setting_key NVARCHAR(80) NOT NULL PRIMARY KEY,
                              setting_value NVARCHAR(200) NOT NULL
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    CREATE TABLE flights (
                             id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                             flight_number NVARCHAR(30) NOT NULL UNIQUE,

                             airline NVARCHAR(100) NOT NULL,

                             origin NVARCHAR(100) NOT NULL,

                             destination NVARCHAR(100) NOT NULL,

                             departure_date DATE NOT NULL,

                             departure_time TIME NOT NULL,

                             arrival_time TIME NOT NULL,

                             base_fare DECIMAL(19,2) NOT NULL,

                             seat_capacity INT NOT NULL,

                             aircraft NVARCHAR(100) NOT NULL,

                             status NVARCHAR(30) NOT NULL DEFAULT 'ON_TIME',

                             delay_minutes INT NULL,

                             departure_airport_id BIGINT NULL,

                             arrival_airport_id BIGINT NULL,

                             is_international BIT NOT NULL DEFAULT 1,

                             CONSTRAINT FK_flights_departure_airport
                                 FOREIGN KEY (departure_airport_id) REFERENCES airports(id),

                             CONSTRAINT FK_flights_arrival_airport
                                 FOREIGN KEY (arrival_airport_id) REFERENCES airports(id),

                             updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

                             CONSTRAINT CK_flights_status
                                 CHECK (status IN (
                                                   'ON_TIME',
                                                   'DELAYED',
                                                   'CANCELLED',
                                                   'COMPLETED'
                                     )),

                             CONSTRAINT CK_flights_capacity
                                 CHECK (seat_capacity > 0),

                             CONSTRAINT CK_flights_fare
                                 CHECK (base_fare > 0)
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       6. SEATS
       ============================================================ */

    CREATE TABLE seats (
                           id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                           flight_id BIGINT NOT NULL,

                           seat_number NVARCHAR(10) NOT NULL,

                           seat_class NVARCHAR(30) NOT NULL,

                           status NVARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',

                           held_by NVARCHAR(80) NULL,

                           held_at DATETIME2 NULL,

                           CONSTRAINT UQ_seats_flight_seat
                               UNIQUE (flight_id, seat_number),

                           CONSTRAINT FK_seats_flight
                               FOREIGN KEY (flight_id)
                                   REFERENCES flights(id),

                           CONSTRAINT CK_seats_status
                               CHECK (status IN (
                                                 'AVAILABLE',
                                                 'HELD',
                                                 'BOOKED',
                                                 'BLOCKED'
                                   ))
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       7. BOOKINGS
       ============================================================ */

    CREATE TABLE bookings (
                              id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                              reference NVARCHAR(40) NOT NULL UNIQUE,

                              customer_id BIGINT NOT NULL,

                              flight_id BIGINT NOT NULL,

                              seat_id BIGINT NOT NULL,

                              passenger_name NVARCHAR(150) NOT NULL,

                              passport_number NVARCHAR(50) NOT NULL,

                              passenger_contact NVARCHAR(30) NOT NULL,

                              seat_class NVARCHAR(30) NULL,

                              base_fare DECIMAL(19,2) NULL,

                              tax DECIMAL(19,2) NULL,

                              service_fee DECIMAL(19,2) NULL,

                              discount DECIMAL(19,2) NOT NULL DEFAULT 0,

                              promo_code NVARCHAR(50) NULL,

                              total_fare DECIMAL(19,2) NULL,

                              status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',

                              payment_id NVARCHAR(255) NULL,

                              ticket_id NVARCHAR(255) NULL,

                              seat_hold_expires_at DATETIME2 NULL,

                              created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

                              modified_at DATETIME2 NULL,

                              updated_at DATETIME2 NULL,

                              CONSTRAINT FK_bookings_customer
                                  FOREIGN KEY (customer_id)
                                      REFERENCES users(id),

                              CONSTRAINT FK_bookings_flight
                                  FOREIGN KEY (flight_id)
                                      REFERENCES flights(id),

                              CONSTRAINT FK_bookings_seat
                                  FOREIGN KEY (seat_id)
                                      REFERENCES seats(id),

                              CONSTRAINT CK_bookings_status
                                  CHECK (status IN (
                                                    'PENDING',
                                                    'CONFIRMED',
                                                    'CANCELLED',
                                                    'COMPLETED'
                                      ))
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       8. BOOKING PASSENGERS
       ============================================================

       Confirmed from the current database:
       booking_id -> bookings.id
       ============================================================ */

    /* ============================================================
       8. BOOKING PASSENGERS

       Each row represents one traveler + their assigned seat within a
       booking, enabling multi-passenger bookings under a single reference.
       seat_id is a real foreign key (rather than a loose text column), so
       seat inventory stays consistent with `seats`. It is intentionally
       NOT unique: rows are never deleted after a cancellation, so a seat
       that is cancelled and released must remain bookable by someone else
       later. Preventing double-booking of an AVAILABLE seat is handled at
       the application layer (pessimistic locking + seat status checks in
       BookingService), the same way bookings.seat_id already works.
       ============================================================ */

    CREATE TABLE booking_passengers (
                                        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                                        booking_id BIGINT NOT NULL,

                                        seat_id BIGINT NOT NULL,

                                        full_name NVARCHAR(100) NOT NULL,

                                        passport_number NVARCHAR(9) NOT NULL,

                                        contact_number NVARCHAR(20) NOT NULL,

                                        CONSTRAINT FK_booking_passengers_booking
                                            FOREIGN KEY (booking_id)
                                                REFERENCES bookings(id),

                                        CONSTRAINT FK_booking_passengers_seat
                                            FOREIGN KEY (seat_id)
                                                REFERENCES seats(id)
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       9. PAYMENTS
       ============================================================ */

    CREATE TABLE payments (
                              id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                              booking_id BIGINT NOT NULL,

                              transaction_id NVARCHAR(80) NOT NULL UNIQUE,

                              amount DECIMAL(19,2) NOT NULL,

                              refund_amount DECIMAL(19,2) NULL,

                              method NVARCHAR(40) NULL,

                              status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',

                              created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

                              updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

                              CONSTRAINT FK_payments_booking
                                  FOREIGN KEY (booking_id)
                                      REFERENCES bookings(id),

                              CONSTRAINT CK_payments_status
                                  CHECK (status IN (
                                                    'PENDING',
                                                    'PAID',
                                                    'VERIFIED',
                                                    'VOID',
                                                    'REFUNDED',
                                                    'FAILED'
                                      ))
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       9B. REFUND REQUESTS

       Customer-submitted disputes/exception requests, reviewed by Finance -
       distinct from the automatic refund RefundPolicyService applies on a
       normal cancellation.
       ============================================================ */

    CREATE TABLE refund_requests (
                                      id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                                      booking_id BIGINT NOT NULL,

                                      customer_id BIGINT NOT NULL,

                                      reason NVARCHAR(500) NOT NULL,

                                      status NVARCHAR(20) NOT NULL DEFAULT 'PENDING',

                                      finance_note NVARCHAR(500) NULL,

                                      resolved_by BIGINT NULL,

                                      created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

                                      resolved_at DATETIME2 NULL,

                                      CONSTRAINT FK_refund_requests_booking
                                          FOREIGN KEY (booking_id)
                                              REFERENCES bookings(id),

                                      CONSTRAINT FK_refund_requests_customer
                                          FOREIGN KEY (customer_id)
                                              REFERENCES users(id),

                                      CONSTRAINT FK_refund_requests_resolved_by
                                          FOREIGN KEY (resolved_by)
                                              REFERENCES users(id),

                                      CONSTRAINT CK_refund_requests_status
                                          CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       10. TICKETS
       ============================================================ */

    CREATE TABLE tickets (
                             id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                             booking_id BIGINT NOT NULL,

                             passenger_id BIGINT NOT NULL,

                             reissued_from_id BIGINT NULL,

                             ticket_number NVARCHAR(80) NOT NULL UNIQUE,

                             qr_value NVARCHAR(500) NULL,

                             status NVARCHAR(30) NOT NULL DEFAULT 'ISSUED',

                             issued_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

                             CONSTRAINT FK_tickets_booking
                                 FOREIGN KEY (booking_id)
                                     REFERENCES bookings(id),

                             CONSTRAINT FK_tickets_reissued_from
                                 FOREIGN KEY (reissued_from_id)
                                     REFERENCES tickets(id),

                             CONSTRAINT FK_tickets_passenger
                                 FOREIGN KEY (passenger_id)
                                     REFERENCES booking_passengers(id),

                             CONSTRAINT CK_tickets_status
                                 CHECK (status IN (
                                                   'ISSUED',
                                                   'VOID',
                                                   'REISSUED'
                                     ))
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       11. CHECK-INS
       ============================================================ */

    CREATE TABLE check_ins (
                               id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                               passenger_id BIGINT NOT NULL UNIQUE,

                               checked_in BIT NOT NULL DEFAULT 0,

                               no_show BIT NOT NULL DEFAULT 0,

                               boarding_pass_number NVARCHAR(80) NULL,

                               checked_at DATETIME2 NULL,

                               CONSTRAINT FK_checkins_passenger
                                   FOREIGN KEY (passenger_id)
                                       REFERENCES booking_passengers(id)
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       12. COMPLAINTS
       ============================================================ */

    CREATE TABLE complaints (
                                id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                                customer_id BIGINT NOT NULL,

                                booking_id BIGINT NULL,

                                reference NVARCHAR(30) NULL,

                                subject NVARCHAR(255) NULL,

                                description NVARCHAR(2000) NULL,

                                status NVARCHAR(30) NOT NULL DEFAULT 'OPEN',

                                response NVARCHAR(2000) NULL,

                                created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

                                updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

                                CONSTRAINT FK_complaints_customer
                                    FOREIGN KEY (customer_id)
                                        REFERENCES users(id),

                                CONSTRAINT FK_complaints_booking
                                    FOREIGN KEY (booking_id)
                                        REFERENCES bookings(id),

                                CONSTRAINT CK_complaints_status
                                    CHECK (status IN (
                                                      'OPEN',
                                                      'IN_PROGRESS',
                                                      'RESOLVED',
                                                      'CLOSED'
                                        ))
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       13. NOTIFICATIONS
       ============================================================

       IMPORTANT:
       Do NOT use the column name "read".
       SQL Server treats READ as a keyword.

       Java:
           private boolean read;

       Java mapping:
           @Column(name = "is_read")

       Database:
           is_read
       ============================================================ */

    CREATE TABLE notifications (
                                   id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                                   user_id BIGINT NOT NULL,

                                   title NVARCHAR(255) NULL,

                                   message NVARCHAR(1000) NULL,

                                   is_read BIT NOT NULL DEFAULT 0,

                                   created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),

                                   CONSTRAINT FK_notifications_user
                                       FOREIGN KEY (user_id)
                                           REFERENCES users(id)
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       14. PROMOTIONS
       ============================================================ */

    /* ============================================================
       14B. CAMPAIGNS
       ============================================================ */

    CREATE TABLE campaigns (
                                id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                                name NVARCHAR(100) NOT NULL,

                                description NVARCHAR(500) NULL,

                                start_date DATE NULL,

                                end_date DATE NULL
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    CREATE TABLE promotions (
                                id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                                code NVARCHAR(50) NOT NULL UNIQUE,

                                percentage DECIMAL(5,2) NOT NULL,

                                valid_from DATE NULL,

                                valid_until DATE NULL,

                                active BIT NOT NULL DEFAULT 1,

                                campaign_id BIGINT NULL,

                                CONSTRAINT CK_promotions_percentage
                                    CHECK (percentage >= 0 AND percentage <= 100),

                                CONSTRAINT FK_promotions_campaign
                                    FOREIGN KEY (campaign_id)
                                        REFERENCES campaigns(id)
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       15. AUDIT LOGS
       ============================================================ */

    CREATE TABLE audit_logs (
                                id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,

                                user_id BIGINT NULL,

                                action NVARCHAR(255) NULL,

                                target NVARCHAR(255) NULL,

                                details NVARCHAR(1000) NULL,

                                created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
    );
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'FRESH')
BEGIN
    /* ============================================================
       16. INDEXES
       ============================================================ */

    CREATE INDEX IX_seats_flight_id
        ON seats(flight_id);

    CREATE INDEX IX_bookings_customer_id
        ON bookings(customer_id);

    CREATE INDEX IX_bookings_flight_id
        ON bookings(flight_id);

    CREATE INDEX IX_bookings_status
        ON bookings(status);

    CREATE INDEX IX_booking_passengers_booking_id
        ON booking_passengers(booking_id);

    CREATE INDEX IX_complaints_customer_id
        ON complaints(customer_id);

    CREATE INDEX IX_complaints_booking_id
        ON complaints(booking_id);

    CREATE INDEX IX_notifications_user_id
        ON notifications(user_id);

    CREATE TABLE flight_operations_officers (
        flight_id BIGINT NOT NULL REFERENCES flights(id),
        user_id BIGINT NOT NULL REFERENCES users(id),
        CONSTRAINT PK_flight_operations_officers PRIMARY KEY (flight_id, user_id)
    );

    CREATE INDEX IX_flights_route_date
        ON flights(origin, destination, departure_date);
END;
GO

/* ============================================================
   UPGRADE SECTION (runs only in MODE 'UPGRADE')
   ============================================================ */

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'UPGRADE')
BEGIN
    /* --- payments: a booking may have several payment attempts --- */
    DECLARE @sql NVARCHAR(MAX) = N'';
    SELECT @sql += N'ALTER TABLE payments DROP CONSTRAINT ' + QUOTENAME(kc.name) + N';'
    FROM sys.key_constraints kc
    JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
    JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
    WHERE kc.parent_object_id = OBJECT_ID('payments') AND kc.type = 'UQ' AND c.name = 'booking_id';
    EXEC sp_executesql @sql;
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'UPGRADE')
BEGIN
    /* --- tickets: several tickets per passenger over time (reissue) --- */
    DECLARE @sql NVARCHAR(MAX) = N'';
    SELECT @sql += N'ALTER TABLE tickets DROP CONSTRAINT ' + QUOTENAME(kc.name) + N';'
    FROM sys.key_constraints kc
    JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
    JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
    WHERE kc.parent_object_id = OBJECT_ID('tickets') AND kc.type = 'UQ' AND c.name = 'passenger_id';
    EXEC sp_executesql @sql;
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'UPGRADE')
BEGIN
    /* Hibernate may also have created the uniqueness as a plain unique index */
    DECLARE @sql2 NVARCHAR(MAX) = N'';
    SELECT @sql2 += N'DROP INDEX ' + QUOTENAME(i.name) + N' ON tickets;'
    FROM sys.indexes i
    JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
    JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
    WHERE i.object_id = OBJECT_ID('tickets') AND i.is_unique = 1 AND i.is_primary_key = 0
      AND c.name = 'passenger_id';
    EXEC sp_executesql @sql2;
    DECLARE @sql3 NVARCHAR(MAX) = N'';
    SELECT @sql3 += N'DROP INDEX ' + QUOTENAME(i.name) + N' ON payments;'
    FROM sys.indexes i
    JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
    JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
    WHERE i.object_id = OBJECT_ID('payments') AND i.is_unique = 1 AND i.is_primary_key = 0
      AND c.name = 'booking_id';
    EXEC sp_executesql @sql3;
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'UPGRADE')
BEGIN
    /* --- ticket status now includes REISSUED --- */
    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_tickets_status')
        ALTER TABLE tickets DROP CONSTRAINT CK_tickets_status;
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'UPGRADE')
BEGIN
    ALTER TABLE tickets ADD CONSTRAINT CK_tickets_status
        CHECK (status IN ('ISSUED', 'VOID', 'REISSUED'));
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'UPGRADE')
BEGIN
    /* --- aircraft: configurable cabin layout --- */
    IF COL_LENGTH('aircraft', 'seats_per_row') IS NULL
        ALTER TABLE aircraft ADD seats_per_row INT NULL DEFAULT 6;
    IF COL_LENGTH('aircraft', 'business_seats') IS NULL
        ALTER TABLE aircraft ADD business_seats INT NULL DEFAULT 6;
END;
GO

IF EXISTS (SELECT 1 FROM ##skylanka_mode WHERE mode = 'UPGRADE')
BEGIN
    /* --- flights <-> operations officers (many-to-many) --- */
    IF OBJECT_ID('flight_operations_officers', 'U') IS NULL
        CREATE TABLE flight_operations_officers (
            flight_id BIGINT NOT NULL REFERENCES flights(id),
            user_id BIGINT NOT NULL REFERENCES users(id),
            CONSTRAINT PK_flight_operations_officers PRIMARY KEY (flight_id, user_id)
        );
END;
GO

DROP TABLE ##skylanka_mode;
GO
