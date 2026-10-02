package com.skylanka.air.shared.entity;

public enum ComplaintStatus {
    OPEN("Under Review"),
    IN_PROGRESS("In Progress"),
    RESOLVED("Resolved"),
    CLOSED("Closed");

    private final String label;

    ComplaintStatus(String label) {
        this.label = label;
    }

    /** Customer-facing wording shown in the complaint tracking portal. */
    public String getLabel() {
        return label;
    }
}
