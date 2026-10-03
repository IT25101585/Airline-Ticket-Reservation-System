package com.skylanka.air.shared.service;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class EmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final boolean enabled;
    private final String from;

    public EmailNotificationService(
            ObjectProvider<JavaMailSender> mailSenders,
            @Value("${notifications.email.enabled:false}") boolean enabled,
            @Value("${notifications.email.from:no-reply@skylankaair.local}") String from) {
        this.mailSenders = mailSenders;
        this.enabled = enabled;
        this.from = from;
    }

    public void bookingCreated(Booking booking) {
        send(bookingEmail(booking), "Booking created: " + booking.getReference(),
                "Your booking " + booking.getReference()
                        + " is pending payment. Total: LKR " + booking.getTotalFare());
    }

    public void paymentCompleted(Booking booking) {
        send(bookingEmail(booking), "Payment received: " + booking.getReference(),
                "Payment for booking " + booking.getReference()
                        + " was approved. Your ticket is available in SkyLanka Air.");
    }

    public void passwordReset(User user, String resetLink) {
        String to = user.getEmail() == null ? null : user.getEmail().toLowerCase(Locale.ROOT);
        String subject = "Reset your SkyLanka Air password";
        String body = "We received a request to reset your password. This link expires in 30 minutes:\n"
                + resetLink + "\nIf you didn't request this, you can ignore this email.";

        if (!enabled || mailSenders.getIfAvailable() == null) {
            log.info("[password reset] Email disabled/unavailable - reset link for {}: {}", to, resetLink);
            return;
        }
        send(to, subject, body);
    }

    private String bookingEmail(Booking booking) {
        if (booking.getCustomer() == null || booking.getCustomer().getEmail() == null) return null;
        return booking.getCustomer().getEmail();
    }

    private void send(String toEmail, String subject, String body) {
        if (!enabled || toEmail == null || toEmail.isBlank()) {
            return;
        }
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null) return;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail.toLowerCase(Locale.ROOT));
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}