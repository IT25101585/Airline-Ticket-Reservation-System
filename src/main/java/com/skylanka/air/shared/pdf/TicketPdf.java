package com.skylanka.air.shared.pdf;

import com.skylanka.air.shared.pdf.PdfCanvas.Face;
import com.skylanka.air.shared.pdf.SkyLankaDoc.Row;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.skylanka.air.shared.pdf.SkyLankaDoc.*;

/** Lays out the SkyLanka Air e-ticket: boarding-pass panel with QR stub, then detail sections. */
public final class TicketPdf {

    private TicketPdf() {
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

    private static void addStrong(List<Row> rows, String label, String value) {
        if (has(value)) rows.add(Row.strong(label, value));
    }

    public static byte[] render(TicketPdfData d) throws IOException {
        SkyLankaDoc doc = new SkyLankaDoc(
                "E-TICKET",
                "Electronic ticket  |  Status: " + or(d.ticketStatus(), "-"),
                List.of("This is a computer-generated e-ticket. Scan the QR code to validate this e-ticket.",
                        "Validation code: " + or(d.qrValue(), "-")),
                "PNR " + or(d.pnr(), "-"));
        PdfCanvas c = doc.canvas();

        boardingPanel(doc, c, d);

        // ---- detail sections
        List<Row> passenger = new ArrayList<>();
        addStrong(passenger, "Passenger name", d.passengerName());
        add(passenger, "Passenger ID", d.passengerId());
        add(passenger, "Passport no.", d.passportNumber());
        add(passenger, "Contact", d.contact());
        add(passenger, "Email", d.email());

        List<Row> booking = new ArrayList<>();
        addStrong(booking, "Booking ref (PNR)", d.pnr());
        add(booking, "Booking ID", d.bookingId());
        add(booking, "Booking date", d.bookingDate());
        add(booking, "Booking status", d.bookingStatus());

        List<Row> seat = new ArrayList<>();
        addStrong(seat, "Seat number", d.seatNumber());
        add(seat, "Cabin class", d.seatClass());

        List<Row> ticket = new ArrayList<>();
        addStrong(ticket, "Ticket number", d.ticketNumber());
        add(ticket, "Ticket status", d.ticketStatus());
        add(ticket, "Issue date", d.issueDate());
        add(ticket, "Reissued from", d.reissuedFrom());

        List<Row> flight = new ArrayList<>();
        addStrong(flight, "Flight number", d.flightNumber());
        add(flight, "Airline", d.airline());
        add(flight, "Aircraft", d.aircraft());
        add(flight, "From", airportLine(d.originCity(), d.originCode(), d.originAirport()));
        add(flight, "To", airportLine(d.destCity(), d.destCode(), d.destAirport()));
        add(flight, "Departure", has(d.date()) && has(d.departureTime()) ? d.date() + ", " + d.departureTime()
                : or(d.date(), d.departureTime()));
        add(flight, "Arrival time", d.arrivalTime());
        add(flight, "Flight status", d.flightStatus());

        String cur = or(d.currency(), "LKR");
        List<Row> fare = new ArrayList<>();
        if (has(d.baseFare())) fare.add(Row.of("Base fare", cur + " " + d.baseFare()));
        if (has(d.tax())) fare.add(Row.of("Taxes", cur + " " + d.tax()));
        if (has(d.serviceFee())) fare.add(Row.of("Service fee", cur + " " + d.serviceFee()));
        if (has(d.discount())) fare.add(Row.of("Discount", cur + " " + d.discount()));
        if (has(d.totalFare())) {
            fare.add(Row.of("Booking total (" + d.passengerCount() + " pax)", cur + " " + d.totalFare()));
        }
        if (has(d.fareShare())) fare.add(Row.strong("Fare share (this ticket)", cur + " " + d.fareShare()));

        List<Row> with = new ArrayList<>();
        if (d.companions() != null) {
            for (TicketPdfData.Companion p : d.companions()) {
                with.add(Row.of("Seat " + or(p.seat(), "-"), or(p.name(), "-")));
            }
        }
        String withTitle = "TRAVELLING WITH (" + with.size() + ")";

        float top = 366f;
        float colW = (CW - 24f) / 2f;
        float labelW = 92f;
        float xr = M + colW + 24f;

        float leftH = doc.measureSection(passenger, colW, labelW) + doc.measureSection(booking, colW, labelW)
                + doc.measureSection(seat, colW, labelW) + doc.measureSection(ticket, colW, labelW);
        float rightH = doc.measureSection(flight, colW, labelW) + doc.measureSection(fare, colW, labelW);
        // (left: passenger, booking, seat, ticket   right: flight, payment summary)

        float end;
        if (top + Math.max(leftH, rightH) <= LIMIT) {
            // Two balanced columns fit on page 1.
            float yl = doc.section("PASSENGER DETAILS", passenger, M, top, colW, labelW);
            yl = doc.section("BOOKING DETAILS", booking, M, yl, colW, labelW);
            yl = doc.section("SEAT DETAILS", seat, M, yl, colW, labelW);
            yl = doc.section("TICKET DETAILS", ticket, M, yl, colW, labelW);
            float yr = doc.section("FLIGHT DETAILS", flight, xr, top, colW, labelW);
            yr = doc.section("PAYMENT SUMMARY", fare, xr, yr, colW, labelW);
            // Companions sit under the payment summary when there is room, keeping the ticket on one page.
            if (!with.isEmpty() && yr + doc.measureSection(with, colW, labelW) <= LIMIT) {
                yr = doc.section(withTitle, with, xr, yr, colW, labelW);
                with = new ArrayList<>();
            }
            end = Math.max(yl, yr);
        } else {
            // Very long values: fall back to full-width sections that may flow onto page 2.
            float y = top;
            float w = CW;
            float lw = 120f;
            y = flowSection(doc, "PASSENGER DETAILS", passenger, y, w, lw);
            y = flowSection(doc, "FLIGHT DETAILS", flight, y, w, lw);
            y = flowSection(doc, "BOOKING DETAILS", booking, y, w, lw);
            y = flowSection(doc, "SEAT DETAILS", seat, y, w, lw);
            y = flowSection(doc, "TICKET DETAILS", ticket, y, w, lw);
            y = flowSection(doc, "PAYMENT SUMMARY", fare, y, w, lw);
            end = y;
        }

        // ---- companions that did not fit beside the payment summary
        if (!with.isEmpty()) {
            flowSection(doc, withTitle, with, end, CW, 92f);
        }

        return doc.finish();
    }

    /** Starts the section on a new page first when not even its first rows would fit. */
    private static float flowSection(SkyLankaDoc doc, String title, List<Row> rows, float y, float w, float lw)
            throws IOException {
        if (rows.isEmpty()) return y;
        float need = 20f + Math.min(doc.measureSection(rows, w, lw) - 36f, 40f);
        if (y + need > LIMIT) y = doc.pageBreak();
        return doc.section(title, rows, M, y, w, lw);
    }

    private static String airportLine(String city, String code, String airport) {
        StringBuilder sb = new StringBuilder();
        if (has(airport)) sb.append(airport);
        else if (has(city)) sb.append(city);
        if (has(code)) sb.append(sb.length() > 0 ? " (" + code + ")" : code);
        return sb.toString();
    }

    // ------------------------------------------------------------------ boarding-pass panel

    private static void boardingPanel(SkyLankaDoc doc, PdfCanvas c, TicketPdfData d) throws IOException {
        final float top = 92f, h = 256f;
        final float stubW = 145f;
        final float mainW = CW - stubW;
        final float pad = 18f;

        c.strokeRect(M, top, CW, h, 0.9f, NAVY);
        c.fillRect(M, top, CW, 22f, PANEL);
        c.line(M, top + 22f, M + CW, top + 22f, 0.6f, RULE, null);
        c.text("E-TICKET ITINERARY", M + pad, top + 15f, Face.BOLD, 8, NAVY);
        c.textRight(or(d.airline(), "SkyLanka Air").toUpperCase(), M + CW - pad, top + 15f, Face.BOLD, 8, NAVY);

        // Perforation between main body and stub.
        float sx = M + mainW;
        c.line(sx, top + 22f, sx, top + h, 0.8f, MUTED, new float[]{3f, 3f});

        // ---- passenger name (shrinks, then wraps to two lines)
        float innerW = mainW - 2 * pad;
        doc.caption("Passenger", M + pad, top + 40f);
        String name = or(d.passengerName(), "-").toUpperCase();
        float ns = c.fitSize(name, Face.BOLD, 16f, 11f, innerW);
        List<String> nameLines = c.wrap(name, Face.BOLD, ns, innerW);
        c.text(nameLines.get(0), M + pad, top + 58f, Face.BOLD, ns, INK);
        if (nameLines.size() > 1) {
            String second = nameLines.get(1);
            if (nameLines.size() > 2) second = second + "...";
            c.text(second, M + pad, top + 58f + ns + 2f, Face.BOLD, ns, INK);
        }

        // ---- FROM -> TO
        float codeW = 118f;
        float lx = M + pad;
        float rx = M + mainW - pad;
        float codeBase = top + 118f;
        routeEnd(c, d.originCode(), d.originCity(), d.originAirport(), lx, codeW, codeBase, false);
        routeEnd(c, d.destCode(), d.destCity(), d.destAirport(), rx - codeW, codeW, codeBase, true);

        // arrow between the two codes
        float ax1 = lx + codeW + 8f, ax2 = rx - codeW - 8f, ay = codeBase - 12f;
        c.line(ax1, ay, ax2 - 6f, ay, 1.4f, NAVY, null);
        c.polygon(new float[]{ax2, ax2 - 8f, ax2 - 8f}, new float[]{ay, ay - 4.5f, ay + 4.5f}, NAVY);

        c.line(M + pad, top + 178f, M + mainW - pad, top + 178f, 0.5f, RULE, null);

        // ---- 3 x 2 info grid
        float gx = M + pad;
        float gw = innerW / 3f;
        cell(doc, c, "Flight", or(d.flightNumber(), "-"), gx, top + 196f, gw - 6f);
        cell(doc, c, "Date", or(d.date(), "-"), gx + gw, top + 196f, gw - 6f);
        cell(doc, c, "Departure", or(d.departureTime(), "-"), gx + 2 * gw, top + 196f, gw - 6f);
        cell(doc, c, "Arrival", or(d.arrivalTime(), "-"), gx, top + 236f, gw - 6f);
        cell(doc, c, "Seat", or(d.seatNumber(), "-"), gx + gw, top + 236f, gw - 6f);
        cell(doc, c, "Class", or(d.seatClass(), "-"), gx + 2 * gw, top + 236f, gw - 6f);

        // ---- stub: QR, verification label, PNR, ticket number
        float cx = sx + stubW / 2f;
        float qr = 118f;
        if (has(d.qrValue())) c.qr(d.qrValue(), cx - qr / 2f, top + 32f, qr);
        c.textCenter("Scan for ticket verification", cx, top + 164f, Face.REGULAR, 7f, MUTED);
        float sw = stubW - 20f;
        c.line(sx + 10f, top + 176f, sx + stubW - 10f, top + 176f, 0.5f, RULE, null);
        c.textCenter("BOOKING REF (PNR)", cx, top + 192f, Face.REGULAR, 7f, MUTED);
        String pnr = or(d.pnr(), "-");
        c.textCenter(pnr, cx, top + 210f, Face.BOLD, c.fitSize(pnr, Face.BOLD, 17f, 8f, sw), INK);
        c.textCenter("TICKET NO.", cx, top + 230f, Face.REGULAR, 7f, MUTED);
        String tn = or(d.ticketNumber(), "-");
        c.textCenter(tn, cx, top + 244f, Face.BOLD, c.fitSize(tn, Face.BOLD, 9f, 6f, sw), INK);
    }

    /** One end of the route: big airport code, city, and (wrapped) airport name. */
    private static void routeEnd(PdfCanvas c, String code, String city, String airport,
                                 float x, float w, float base, boolean alignRight) throws IOException {
        boolean haveCode = has(code);
        String big = haveCode ? code.toUpperCase() : or(city, "-").toUpperCase();
        float size = c.fitSize(big, Face.BOLD, 34f, 12f, w);
        float bw = c.width(big, Face.BOLD, size);
        float bx = alignRight ? x + w - bw : x;
        c.text(big, bx, base, Face.BOLD, size, NAVY);

        float y = base + 15f;
        if (haveCode && has(city)) {
            y = wrapped(c, city.toUpperCase(), Face.BOLD, 9f, w, y, x, alignRight, INK, 1);
        }
        if (has(airport)) {
            wrapped(c, airport, Face.REGULAR, 7f, w, y - (haveCode && has(city) ? 0 : 0), x, alignRight, MUTED, 2);
        }
    }

    private static float wrapped(PdfCanvas c, String s, Face f, float size, float w, float y, float x,
                                 boolean right, int rgb, int maxLines) throws IOException {
        List<String> lines = c.wrap(s, f, size, w);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            String line = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) line = line + "...";
            if (right) c.textRight(line, x + w, y, f, size, rgb);
            else c.text(line, x, y, f, size, rgb);
            y += size + 2.5f;
        }
        return y;
    }

    private static void cell(SkyLankaDoc doc, PdfCanvas c, String label, String value, float x, float top, float w)
            throws IOException {
        doc.caption(label, x, top);
        float size = c.fitSize(value, Face.BOLD, 13f, 7.5f, w);
        c.text(value, x, top + 16f, Face.BOLD, size, INK);
    }
}
