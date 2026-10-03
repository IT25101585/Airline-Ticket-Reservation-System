package com.skylanka.air.shared.repository;

import com.skylanka.air.shared.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
