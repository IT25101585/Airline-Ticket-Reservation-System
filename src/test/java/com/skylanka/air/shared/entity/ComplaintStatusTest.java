package com.skylanka.air.shared.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintStatusTest {

    @Test
    void customerFacingLabelsMatchTheDesignDocument() {
        assertEquals("Under Review", ComplaintStatus.OPEN.getLabel());
        assertEquals("In Progress", ComplaintStatus.IN_PROGRESS.getLabel());
        assertEquals("Resolved", ComplaintStatus.RESOLVED.getLabel());
    }

    @Test
    void complaintGetsATrackingReferenceOnPersist() throws Exception {
        Complaint c = new Complaint();
        var m = Complaint.class.getDeclaredMethod("assignReference");
        m.setAccessible(true);
        m.invoke(c);
        assertTrue(c.getReference().matches("CMP-[0-9A-F]{8}"));
        assertEquals(c.getReference(), c.getDisplayReference());
    }
}
