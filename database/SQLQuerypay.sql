CREATE TABLE bookings (
    booking_id INT PRIMARY KEY,
    status VARCHAR(50) DEFAULT 'Pending'
);

CREATE TABLE payments (
    payment_id INT IDENTITY(1,1) PRIMARY KEY,
    booking_id INT NOT NULL,
    user_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    card_number VARCHAR(20) NOT NULL,
    transaction_id VARCHAR(50) UNIQUE NOT NULL,
    payment_status VARCHAR(50) NOT NULL,
    invoice_number VARCHAR(50) UNIQUE,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (booking_id) REFERENCES bookings(booking_id)
);

-- Sample Data
INSERT INTO bookings (booking_id, status) VALUES (10045, 'Pending'), (10046, 'Pending');



select * from payments 
