package com.skylanka.air.shared.security;

import org.springframework.stereotype.Component;

@Component
public class NavPermissions {
    private final AuthorizationSupport authz;

    public NavPermissions(AuthorizationSupport authz) {
        this.authz = authz;
    }

    public boolean isSignedIn() {
        return authz.isAuthenticated();
    }

    public boolean canViewCustomerDashboard() {
        return authz.hasAnyRole("CUSTOMER");
    }

    public boolean canViewOperations() {
        return authz.hasAnyRole("OPERATIONS", "ADMIN");
    }

    public boolean canViewCheckin() {
        return authz.hasAnyRole("OPERATIONS", "ADMIN");
    }

    public boolean canViewSupport() {
        return authz.hasAnyRole("SUPPORT", "ADMIN");
    }

    /** Transaction and refund records are restricted to the Finance role. */
    public boolean canViewFinance() {
        return authz.hasAnyRole("FINANCE");
    }

    public boolean canViewReports() {
        return authz.hasAnyRole("FINANCE", "ADMIN");
    }

    public boolean canViewMarketing() {
        return authz.hasAnyRole("MARKETING", "ADMIN");
    }

    public boolean canViewAdmin() {
        return authz.hasAnyRole("ADMIN");
    }

    /**
     * True only for the ADMIN role. Used to keep the staff navbar from showing
     * every functional area at once for admins (who have access to all of them
     * via canView*() above) - those shortcuts live on the admin dashboard
     * instead. Non-admin staff still see their own one or two areas in the nav
     * as before.
     */
    /** True for any non-customer (staff) role. Shared pages use this to pick the staff shell. */
    public boolean isStaff() {
        return authz.hasAnyRole("OPERATIONS", "SUPPORT", "FINANCE", "MARKETING", "ADMIN");
    }

    public boolean isAdmin() {
        return authz.hasAnyRole("ADMIN");
    }
}
