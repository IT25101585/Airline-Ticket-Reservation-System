package com.skylanka.air.shared.repository;

import com.skylanka.air.shared.entity.Promotion;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCode(String code);
}
