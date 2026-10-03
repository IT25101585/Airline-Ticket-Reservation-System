package com.skylanka.air.shared.repository;

import com.skylanka.air.shared.entity.Notification;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long id);
}
