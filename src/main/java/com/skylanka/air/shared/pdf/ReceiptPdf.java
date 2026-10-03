package com.skylanka.air.shared.pdf;

import com.skylanka.air.shared.pdf.PdfCanvas.Face;
import com.skylanka.air.shared.pdf.SkyLankaDoc.Row;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.skylanka.air.shared.pdf.SkyLankaDoc.*;

/** Lays out the SkyLanka Air payment receipt: meta strip, detail sections, price table, footer. */
public final class ReceiptPdf {

    private ReceiptPdf() {
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    private static String or(String s, String fallback) {
        return has(s) ? s : fallback;
    }

    private static void add(List<Row> rows, String label, String value) {
        if (has(value)) rows.add(Row.of(label, value));
    }

    public static byte[] render(ReceiptPdfData d) throws IOException {
        SkyLankaDoc doc = new SkyLankaDoc(
                "PAYMENT RECEIPT",
                "Travel receipt  |  " + or(d.receiptNumber(), "-"),
                List.of("Thank you for choosing SkyLanka Air.  This is a computer-generated receipt.",
                        "Booking reference: " + or(d.bookingReference(), "-")
                                + "   Transaction ID: " + or(d.transactionId(), "-")),
                or(d.receiptNumber(), "Receipt"));
        PdfCanvas c = doc.canvas();

        metaStrip(c, d);

        // ---- customer + flight columns
        List<Row> customer = new ArrayList<>();
        customer.add(Row.strong("Passenger / customer", or(d.customerName(), "-")));
        add(customer, "Email", d.email());
        add(customer, "Contact", d.contact());
        customer.add(Row.of("Booking reference", or(d.bookingReference(), "-")));
        if (d.passengers() != null && d.passengers().size() > 1) {
            customer.add(Row.of("Passengers", String.join(", ", d.passengers())));
        }

        List<Row> flight = new ArrayList<>();
        flight.add(Row.strong("Flight number", or(d.flightNumber(), "-")));
        add(flight, "Airline", d.airline());
        add(flight, "Route", d.route());
        add(flight, "Travel date", d.travelDate());
        add(flight, "Departure time", d.departureTime());
        add(flight, "Cabin class", d.seatClass());
        add(flight, "Booking ID", d.bookingId());
        add(flight, "Booking date", d.bookingDate());
        add(flight, "Booking status", d.bookingStatus());

        List<Row> pay = new ArrayList<>();
        if ("CARD".equals(d.methodKind())) {
            // Only the network is shown: the system never stores card numbers, expiry or CVV.
            pay.add(Row.strong("Payment method", "Credit / Debit Card"));
            add(pay, "Card network", d.methodLabel());
        } else {
            pay.add(Row.strong("Payment method", or(d.methodLabel(), "-")));
            if ("BANK_TRANSFER".equals(d.methodKind())) add(pay, "Transfer reference", d.transactionId());
        }
        pay.add(Row.of("Transaction ID", or(d.transactionId(), "-")));
        add(pay, "Payment date", d.paymentDate());
        pay.add(Row.strong("Payment status", or(d.paymentStatus(), "-")));

        float top = 156f;
        float colW = (CW - 24f) / 2f;
        float xr = M + colW + 24f;
        float lw = 92f;
        float left = doc.measureSection(customer, colW, lw);
        float right = doc.measureSection(flight, colW, lw);
        float y;
        if (top + Math.max(left, right) <= LIMIT - 250f) {
            float yl = doc.section("CUSTOMER DETAILS", customer, M, top, colW, lw);
            float yr = doc.section("FLIGHT / BOOKING DETAILS", flight, xr, top, colW, lw);
            y = Math.max(yl, yr);
        } else {
            y = doc.section("CUSTOMER DETAILS", customer, M, top, CW, 120f);
            y = doc.section("FLIGHT / BOOKING DETAILS", flight, M, y, CW, 120f);
        }
        if (y + doc.measureSection(pay, CW, 120f) > LIMIT - 200f) y = doc.pageBreak();
        y = doc.section("PAYMENT DETAILS", pay, M, y, CW, 120f);

        // ---- price table (kept together on one page)
        float tableH = 22f + 6 * 22f + 40f;
        if (y + tableH > LIMIT) y = doc.pageBreak();
        priceTable(c, d, y);

        return doc.finish();
    }

    /** Receipt no. / date / booking ref / status band directly under the header. */
    private static void metaStrip(PdfCanvas c, ReceiptPdfData d) throws IOException {
        float top = 92f, h = 46f;
        c.fillRect(M, top, CW, h, PANEL);
        c.strokeRect(M, top, CW, h, 0.6f, RULE);
        float w = CW / 4f;
        String[][] cells = {
                {"Receipt number", d.receiptNumber()},
                {"Receipt date", d.receiptDate()},
                {"Booking reference", d.bookingReference()},
                {"Payment status", d.paymentStatus()}};
        for (int i = 0; i < 4; i++) {
            float x = M + i * w + 12f;
            c.text(cells[i][0].toUpperCase(), x, top + 17f, Face.REGULAR, 7f, MUTED);
            String v = or(cells[i][1], "-");
            float size = c.fitSize(v, Face.BOLD, 11f, 7f, w - 20f);
            c.text(v, x, top + 34f, Face.BOLD, size, INK);
            if (i > 0) c.line(M + i * w, top + 8f, M + i * w, top + h - 8f, 0.5f, RULE, null);
        }
    }

    private static void priceTable(PdfCanvas c, ReceiptPdfData d, float y) throws IOException {
        String cur = or(d.currency(), "LKR");
        c.text("PRICE BREAKDOWN", M, y + 9f, Face.BOLD, 8.5f, NAVY);
        c.line(M, y + 14f, M + CW, y + 14f, 1.1f, NAVY, null);
        y += 20f;

        // header row
        c.fillRect(M, y, CW, 20f, PANEL);
        c.text("DESCRIPTION", M + 10f, y + 13.5f, Face.BOLD, 7.5f, MUTED);
        c.textRight("AMOUNT (" + cur + ")", M + CW - 10f, y + 13.5f, Face.BOLD, 7.5f, MUTED);
        y += 20f;

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Base fare", or(d.baseFare(), "-")});
        rows.add(new String[]{"Taxes", or(d.tax(), "-")});
        rows.add(new String[]{"Service / booking fee", or(d.serviceFee(), "-")});
        String disc = or(d.discount(), "0.00");
        boolean noDiscount = disc.equals("0.00");
        String discLabel = "Discount" + (has(d.promoCode()) ? " (promo " + d.promoCode() + ")" : "");
        rows.add(new String[]{discLabel, noDiscount ? disc : "-" + disc});

        for (String[] r : rows) {
            List<String> lines = c.wrap(r[0], Face.REGULAR, 9.5f, CW - 150f);
            float h = Math.max(22f, lines.size() * 12f + 10f);
            float ly = y + 15f;
            for (String line : lines) {
                c.text(line, M + 10f, ly, Face.REGULAR, 9.5f, INK);
                ly += 12f;
            }
            c.textRight(r[1], M + CW - 10f, y + 15f, Face.REGULAR, 9.5f, INK);
            y += h;
            c.line(M, y, M + CW, y, 0.4f, RULE, null);
        }

        if (has(d.refunded())) {
            c.text("Refunded", M + 10f, y + 15f, Face.REGULAR, 9.5f, INK);
            c.textRight("-" + d.refunded(), M + CW - 10f, y + 15f, Face.REGULAR, 9.5f, INK);
            y += 22f;
            c.line(M, y, M + CW, y, 0.4f, RULE, null);
        }

        // prominent total
        y += 8f;
        c.fillRect(M, y, CW, 34f, NAVY);
        c.text("TOTAL PAID", M + 12f, y + 22f, Face.BOLD, 12f, WHITE);
        String total = cur + " " + or(d.totalPaid(), "-");
        c.textRight(total, M + CW - 12f, y + 23f, Face.BOLD, c.fitSize(total, Face.BOLD, 16f, 9f, CW - 140f), WHITE);
        y += 34f;

        c.textCenter("Thank you for choosing SkyLanka Air.", M + CW / 2f, y + 30f, Face.BOLD, 11f, NAVY);
    }
}
