package com.skylanka.air.shared.repository;

import com.skylanka.air.shared.entity.Complaint;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByCustomerIdOrderByCreatedAtDesc(Long id);

    List<Complaint> findAllByOrderByCreatedAtDesc();

    long countByStatus(com.skylanka.air.shared.entity.ComplaintStatus status);
}
