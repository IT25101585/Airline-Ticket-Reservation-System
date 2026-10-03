package com.skylanka.air.shared.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.*;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String action;
    private String target;
    @Column(length = 1000)
    private String details;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long v) {
        userId = v;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String v) {
        action = v;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String v) {
        target = v;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String v) {
        details = v;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
