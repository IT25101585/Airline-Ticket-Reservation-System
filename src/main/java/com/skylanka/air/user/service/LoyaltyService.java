package com.skylanka.air.user.service;

import com.skylanka.air.user.entity.User;
import com.skylanka.air.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Customer loyalty points: 1 point per LKR 100 of fare paid; reversed when the payment is refunded/voided. */
@Service
public class LoyaltyService {
    private static final BigDecimal LKR_PER_POINT = new BigDecimal("100");

    private final UserRepository users;

    public LoyaltyService(UserRepository users) {
        this.users = users;
    }

    public static int pointsFor(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return 0;
        return amount.divideToIntegralValue(LKR_PER_POINT).intValue();
    }

    public void award(User customer, BigDecimal amount) {
        adjust(customer, pointsFor(amount));
    }

    public void revoke(User customer, BigDecimal amount) {
        adjust(customer, -pointsFor(amount));
    }

    private void adjust(User customer, int delta) {
        if (customer == null || customer.getId() == null || delta == 0) return;
        users.findById(customer.getId()).ifPresent(u -> {
            u.setLoyaltyPoints(u.getLoyaltyPoints() + delta);
            users.save(u);
        });
    }
}
