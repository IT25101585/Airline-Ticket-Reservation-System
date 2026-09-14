package com.skylanka.ticketmanagement.service;

import com.skylanka.ticketmanagement.entity.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Notification service for ticket lifecycle events.
 *
 * Currently implemented as a stub that logs to the console.
 * Replace the log statements with your actual email / SMS / push notification
 * integration (e.g., Spring Mail, Twilio, Firebase).
 *
 * Integration points:
 *   onTicketGenerated()  → send "Your e-ticket is ready" email to passenger
 *   onTicketReissued()   → send "Your ticket has been reissued" email
 *   onTicketVoided()     → send "Your ticket has been voided" email
 *   onTicketCancelled()  → send "Your booking has been cancelled" email
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void onTicketGenerated(Ticket ticket) {
        log.info("[NOTIFICATION] Ticket GENERATED — ticketNumber={}, passenger={}, email={}",
                ticket.getTicketNumber(),
                ticket.getPassenger().getFullName(),
                ticket.getUser().getEmail());
        // TODO: send email via Spring Mail
        // mailService.sendTicketGeneratedEmail(ticket);
    }

    public void onTicketReissued(Ticket oldTicket, Ticket newTicket) {
        log.info("[NOTIFICATION] Ticket REISSUED — old={}, new={}, passenger={}",
                oldTicket.getTicketNumber(),
                newTicket.getTicketNumber(),
                newTicket.getPassenger().getFullName());
        // TODO: send email via Spring Mail
        // mailService.sendTicketReissuedEmail(newTicket);
    }

    public void onTicketVoided(Ticket ticket, String reason) {
        log.info("[NOTIFICATION] Ticket VOIDED — ticketNumber={}, reason={}",
                ticket.getTicketNumber(), reason);
        // TODO: send email via Spring Mail
        // mailService.sendTicketVoidedEmail(ticket, reason);
    }

    public void onTicketCancelled(Ticket ticket, String reason) {
        log.info("[NOTIFICATION] Ticket CANCELLED — ticketNumber={}, reason={}",
                ticket.getTicketNumber(), reason);
        // TODO: send email via Spring Mail
        // mailService.sendTicketCancelledEmail(ticket, reason);
    }
}
