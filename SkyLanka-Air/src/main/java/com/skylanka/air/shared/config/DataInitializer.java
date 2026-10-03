package com.skylanka.air.shared.config;

import com.skylanka.air.flight.entity.Flight;
import com.skylanka.air.flight.repository.FlightRepository;
import com.skylanka.air.flight.service.AirportService;
import com.skylanka.air.seat.service.SeatService;
import com.skylanka.air.user.entity.User;
import com.skylanka.air.user.repository.UserRepository;
import com.skylanka.air.shared.entity.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.*;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner seed(UserRepository users, FlightRepository flights, SeatService seatService, PasswordEncoder encoder,
                           AirportService airportService) {
        return args -> {
            airportService.seedDefaults();
            migrateLegacyBrand(users, flights);
            createUser(users, encoder, "Demo Customer", "demo@skylankaair.com", "demo123", Role.CUSTOMER);
            users.findByEmail("demo@skylankaair.com").ifPresent(d -> {
                if (d.getCreatedAt() != null && !d.isMarketingOptIn() && d.getLoyaltyPoints() == 0) {
                    d.setMarketingOptIn(true); // demo customer opted in so the marketing screens have an audience
                    users.save(d);
                }
            });
            createUser(users, encoder, "Operations Officer", "ops@skylankaair.com", "ops123", Role.OPERATIONS);
            createUser(users, encoder, "Finance Officer", "finance@skylankaair.com", "finance123", Role.FINANCE);
            createUser(users, encoder, "Support Officer", "support@skylankaair.com", "support123", Role.SUPPORT);
            createUser(users, encoder, "Marketing Executive", "marketing@skylankaair.com", "marketing123", Role.MARKETING);
            createUser(users, encoder, "System Administrator", "admin@skylankaair.com", "admin123", Role.ADMIN);
            if (flights.count() == 0) {
                add(flights, seatService, airportService, "SK101", "SkyLanka Air", "Colombo", "Dubai", LocalDate.now().plusDays(3), LocalTime.of(8, 30), LocalTime.of(11, 45), new BigDecimal("52000.00"), 24, "Airbus A320");
                add(flights, seatService, airportService, "SK205", "SkyLanka Air", "Colombo", "Singapore", LocalDate.now().plusDays(5), LocalTime.of(10, 0), LocalTime.of(16, 20), new BigDecimal("68000.00"), 30, "Airbus A321");
                add(flights, seatService, airportService, "SK310", "SkyLanka Air", "Colombo", "Male", LocalDate.now().plusDays(8), LocalTime.of(14, 15), LocalTime.of(15, 35), new BigDecimal("36000.00"), 18, "Airbus A320");
            } else {
                flights.findAll().forEach(f -> seatService.ensureSeats(f));
            }
            // Databases created before airports existed: link flights to their airports once.
            flights.findAll().forEach(f -> {
                if (f.getDepartureAirport() == null || f.getArrivalAirport() == null) {
                    if (airportService.link(f)) flights.save(f);
                }
            });
            // Staff accounts carry an employee id and department.
            users.findAll().forEach(u -> {
                if (u.isStaff() && u.getEmployeeId() == null) {
                    u.assignStaffProfile();
                    users.save(u);
                }
            });
        };
    }

    /**
     * Databases created before the SkyLanka Air rebrand hold demo accounts on the old mail domain and
     * flights with the old airline name. Rename them once so existing installs keep working.
     */
    private void migrateLegacyBrand(UserRepository users, FlightRepository flights) {
        String legacyDomain = "@sky" + "linkair.com";
        for (String local : new String[]{"demo", "ops", "finance", "support", "marketing", "admin"}) {
            String fresh = local + "@skylankaair.com";
            if (users.findByEmail(fresh).isPresent()) continue;
            users.findByEmail(local + legacyDomain).ifPresent(u -> {
                u.setEmail(fresh);
                users.save(u);
            });
        }
        flights.findAll().forEach(f -> {
            String airline = f.getAirline();
            if (airline != null && airline.replace(" ", "").equalsIgnoreCase("sky" + "linkair")) {
                f.setAirline("SkyLanka Air");
                flights.save(f);
            }
        });
    }

    private void createUser(UserRepository r, PasswordEncoder e, String n, String email, String pass, Role role) {
        if (r.findByEmail(email).isEmpty()) {
            User u = new User();
            u.setName(n);
            u.setEmail(email);
            u.setPassword(e.encode(pass));
            u.setContactNumber("0770000000");
            u.setRole(role);
            User saved = r.save(u);
            if (saved.isStaff()) {
                saved.assignStaffProfile();
                r.save(saved);
            }
        }
    }

    private void add(FlightRepository r, SeatService ss, AirportService airports, String no, String airline, String o, String d, LocalDate date, LocalTime dep, LocalTime arr, BigDecimal fare, int cap, String aircraft) {
        Flight f = new Flight();
        f.setFlightNumber(no);
        f.setAirline(airline);
        f.setOrigin(o);
        f.setDestination(d);
        f.setDepartureDate(date);
        f.setDepartureTime(dep);
        f.setArrivalTime(arr);
        f.setBaseFare(fare);
        f.setSeatCapacity(cap);
        f.setAircraft(aircraft);
        airports.link(f);
        r.save(f);
        ss.createSeats(f);
    }
}
