package com.skylanka.air.shared.repository;

import com.skylanka.air.shared.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
}
