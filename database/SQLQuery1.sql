CREATE DATABASE SkyLankaDB;
GO

USE SkyLankaDB;
GO

CREATE TABLE Seats (
    seat_id VARCHAR(10) NOT NULL,
    flight_id VARCHAR(10) NOT NULL,
    cabin_class VARCHAR(20),
    price DECIMAL(10,2),
    is_booked BIT DEFAULT 0,
    PRIMARY KEY (seat_id, flight_id)
);

-- Insert sample row to test loading
INSERT INTO Seats (seat_id, flight_id, cabin_class, price, is_booked) 
VALUES ('1A', 'UL-302', 'Business', 450000.00, 0);

select * from Seats