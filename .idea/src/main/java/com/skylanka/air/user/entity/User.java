package com.skylanka.air.user.entity;

import com.skylanka.air.shared.entity.Role;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank
    private String name;
    @Email
    @NotBlank
    @Column(unique = true, nullable = false)
    private String email;
    @NotBlank
    @Size(min = 6)
    private String password;
    @NotBlank
    private String contactNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CUSTOMER;
    private boolean active = true;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime createdAt = LocalDateTime.now();
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    private LocalDateTime lastLogin;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String v) {
        name = v;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String v) {
        email = v;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String v) {
        password = v;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String v) {
        contactNumber = v;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role v) {
        role = v;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean v) {
        active = v;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime v) {
        createdAt = v;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime v) {
        lastLogin = v;
    }

    // ---- Customer attributes -------------------------------------------------
    @Column(length = 20)
    private String passportNumber;
    @Column(nullable = false, columnDefinition = "int not null default 0")
    private int loyaltyPoints = 0;
    /** Opt-in for marketing newsletters / promotions. Transactional notifications are always sent. */
    @Column(name = "marketing_opt_in", nullable = false, columnDefinition = "bit not null default 0")
    private boolean marketingOptIn = false;
    /** True for accounts created implicitly by guest checkout (no chosen password). */
    @Column(name = "is_guest", nullable = false, columnDefinition = "bit not null default 0")
    private boolean guest = false;

    // ---- Staff attributes ----------------------------------------------------
    @Column(length = 20)
    private String employeeId;
    @Column(length = 60)
    private String department;

    public String getPassportNumber() {
        return passportNumber;
    }

    public void setPassportNumber(String v) {
        passportNumber = v == null || v.isBlank() ? null : v.trim();
    }

    public int getLoyaltyPoints() {
        return loyaltyPoints;
    }

    public void setLoyaltyPoints(int v) {
        loyaltyPoints = Math.max(0, v);
    }

    public boolean isMarketingOptIn() {
        return marketingOptIn;
    }

    public void setMarketingOptIn(boolean v) {
        marketingOptIn = v;
    }

    public boolean isGuest() {
        return guest;
    }

    public void setGuest(boolean v) {
        guest = v;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String v) {
        employeeId = v;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String v) {
        department = v;
    }

    public boolean isStaff() {
        return role != null && role != Role.CUSTOMER;
    }

    /**
     * Staff (Operations, Support, Finance, Marketing, Admin) carry an employee id and department;
     * customers do not. Call after the user has been saved once so the id is available.
     */
    public void assignStaffProfile() {
        if (!isStaff()) {
            employeeId = null;
            department = null;
            return;
        }
        if (employeeId == null && id != null) {
            employeeId = String.format("EMP-%04d", id);
        }
        department = switch (role) {
            case OPERATIONS -> "Flight Operations";
            case SUPPORT -> "Customer Support";
            case FINANCE -> "Finance";
            case MARKETING -> "Marketing";
            case ADMIN -> "IT & Administration";
            default -> null;
        };
    }
}
