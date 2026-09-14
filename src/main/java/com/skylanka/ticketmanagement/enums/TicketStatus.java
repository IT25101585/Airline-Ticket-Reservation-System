package com.skylanka.ticketmanagement.enums;

/**
 * Represents the lifecycle status of an electronic ticket.
 *
 * Valid transitions:
 *   PAID (booking) → ACTIVE
 *   ACTIVE         → REISSUED
 *   ACTIVE         → VOID
 *   ACTIVE         → CANCELLED
 *   REISSUED       → VOID
 *
 * VOID and CANCELLED are terminal states — no further transitions allowed.
 */
public enum TicketStatus {
    ACTIVE,
    REISSUED,
    VOID,
    CANCELLED;

    /**
     * Returns true if this status is a terminal state
     * (no further status changes are permitted).
     */
    public boolean isTerminal() {
        return this == VOID || this == CANCELLED;
    }

    /**
     * Validates whether a status transition from this status to
     * the target status is permitted by business rules.
     *
     * @param target the desired new status
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(TicketStatus target) {
        return switch (this) {
            case ACTIVE   -> target == REISSUED || target == VOID || target == CANCELLED;
            case REISSUED -> target == VOID;
            case VOID, CANCELLED -> false; // terminal states
        };
    }
}
