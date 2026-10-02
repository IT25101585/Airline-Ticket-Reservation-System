package com.skylanka.air.user.service;

import com.skylanka.air.user.entity.User;
import com.skylanka.air.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class LoyaltyServiceTest {

    @Test
    void onePointPerHundredRupees() {
        assertEquals(0, LoyaltyService.pointsFor(new BigDecimal("99.99")));
        assertEquals(1, LoyaltyService.pointsFor(new BigDecimal("100")));
        assertEquals(520, LoyaltyService.pointsFor(new BigDecimal("52000.00")));
        assertEquals(0, LoyaltyService.pointsFor(null));
    }

    @Test
    void awardThenRevokeReturnsToZeroAndNeverGoesNegative() throws Exception {
        UserRepository repo = mock(UserRepository.class);
        User u = mock(User.class);
        when(u.getId()).thenReturn(7L);
        User stored = new User();
        when(repo.findById(7L)).thenReturn(Optional.of(stored));
        LoyaltyService svc = new LoyaltyService(repo);

        svc.award(u, new BigDecimal("1000"));
        assertEquals(10, stored.getLoyaltyPoints());
        svc.revoke(u, new BigDecimal("5000"));
        assertEquals(0, stored.getLoyaltyPoints());
    }
}
