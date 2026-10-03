package com.skylanka.air.shared.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.*;

@Entity
@Table(name = "promotions")
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String code;
    private double percentage;
    @JdbcTypeCode(SqlTypes.DATE)
    private LocalDate validFrom;
    @JdbcTypeCode(SqlTypes.DATE)
    private LocalDate validUntil;
    private boolean active = true;
    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String v) {
        code = v;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double v) {
        percentage = v;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate v) {
        validFrom = v;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate v) {
        validUntil = v;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean v) {
        active = v;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public void setCampaign(Campaign v) {
        campaign = v;
    }
}
