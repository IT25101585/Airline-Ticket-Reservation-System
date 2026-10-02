package com.skylanka.air.user.service;

import com.skylanka.air.shared.entity.Role;
import com.skylanka.air.user.entity.User;
import com.skylanka.air.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {
    private final UserRepository repo;
    private final PasswordEncoder encoder;

    public UserService(UserRepository r, PasswordEncoder e) {
        repo = r;
        encoder = e;
    }

    /** Registers a customer. Server-managed fields are never taken from the submitted form. */
    public User register(User u) {
        if (repo.findByEmail(u.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email is already registered.");
        }
        u.setPassword(encoder.encode(u.getPassword()));
        u.setRole(Role.CUSTOMER);
        u.setGuest(false);
        u.setActive(true);
        u.setLoyaltyPoints(0);
        u.setEmployeeId(null);
        u.setDepartment(null);
        return repo.save(u);
    }

    /** Creates the lightweight account behind a guest checkout (random password, cannot log in until reset/registered). */
    public User createGuest(String name, String email, String contact) {
        User g = new User();
        g.setName(name.trim());
        g.setEmail(email.trim().toLowerCase());
        g.setContactNumber(contact.trim());
        g.setPassword(encoder.encode(UUID.randomUUID().toString()));
        g.setRole(Role.CUSTOMER);
        g.setGuest(true);
        return repo.save(g);
    }

    public boolean matches(User u, String raw) {
        return encoder.matches(raw, u.getPassword());
    }

    /**
     * "Delete account": personal data is erased and the account is deactivated. Booking and payment
     * rows are kept (financial record-keeping) but no longer identify the person.
     */
    public void anonymise(User u) {
        u.setName("Deleted User");
        u.setEmail("deleted-" + u.getId() + "@deleted.invalid");
        u.setContactNumber("0000000");
        u.setPassportNumber(null);
        u.setMarketingOptIn(false);
        u.setLoyaltyPoints(0);
        u.setActive(false);
        u.setGuest(false);
        u.setPassword(encoder.encode(UUID.randomUUID().toString()));
        repo.save(u);
    }
}
