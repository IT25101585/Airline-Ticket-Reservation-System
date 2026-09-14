package com.skylanka.ticketmanagement.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.skylanka.ticketmanagement.entity.Ticket;
import com.skylanka.ticketmanagement.enums.TicketStatus;
import com.skylanka.ticketmanagement.exception.AppException;
import com.skylanka.ticketmanagement.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Generates a professional e-ticket PDF for a given Ticket entity.
 *
 * Layout:
 *   ┌─────────────────────────────────────────┐
 *   │  SKYLANKA AIR TRAVELS     [logo area]   │
 *   │  Electronic Boarding Pass               │
 *   ├──────────────────────────┬──────────────┤
 *   │  Passenger Information   │  QR Code     │
 *   │  Journey Details         │              │
 *   ├──────────────────────────┴──────────────┤
 *   │  Ticket Information                     │
 *   │  [VOID / REISSUED banner if applicable] │
 *   └─────────────────────────────────────────┘
 *
 * No sensitive payment data is included in the PDF.
 */
@Service
public class PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");
    private static final DateTimeFormatter D_FMT  = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final DeviceRgb BRAND_BLUE  = new DeviceRgb(0,  82, 155);
    private static final DeviceRgb BRAND_GOLD  = new DeviceRgb(204,163,  0);
    private static final DeviceRgb LIGHT_GRAY  = new DeviceRgb(245,245,245);

    private final QRCodeService qrCodeService;

    public PdfService(QRCodeService qrCodeService) {
        this.qrCodeService = qrCodeService;
    }

    /**
     * Generate a PDF byte array for the given ticket.
     *
     * @param ticket the fully-loaded Ticket entity
     * @return raw PDF bytes ready to stream to the HTTP response
     */
    public byte[] generateTicketPdf(Ticket ticket) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer  = new PdfWriter(baos);
            PdfDocument pdfDoc  = new PdfDocument(writer);
            Document    document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(30, 36, 30, 36);

            PdfFont fontBold    = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont fontNormal  = PdfFontFactory.createFont("Helvetica");

            // ── Header ──────────────────────────────────────────────────────
            addHeader(document, fontBold, fontNormal, ticket);

            // ── VOID / REISSUED warning banner ───────────────────────────────
            if (ticket.getTicketStatus() != TicketStatus.ACTIVE) {
                addStatusBanner(document, fontBold, ticket.getTicketStatus());
            }

            // ── Body: two-column table (details | QR) ───────────────────────
            addBodyTable(document, fontBold, fontNormal, ticket);

            // ── Footer ───────────────────────────────────────────────────────
            addFooter(document, fontNormal);

            document.close();
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("PDF generation failed for ticket {}: {}", ticket.getId(), e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.PDF_GENERATION_FAILED,
                    "Failed to generate PDF: " + e.getMessage());
        }
    }

    // =========================================================================
    // Private layout helpers
    // =========================================================================

    private void addHeader(Document doc, PdfFont bold, PdfFont normal, Ticket ticket)
            throws IOException {

        // Airline name
        Paragraph airlineName = new Paragraph("✈  SKYLANKA AIR TRAVELS")
                .setFont(bold).setFontSize(20)
                .setFontColor(BRAND_BLUE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2);

        Paragraph tagLine = new Paragraph("Electronic Boarding Pass")
                .setFont(normal).setFontSize(10)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(12);

        doc.add(airlineName);
        doc.add(tagLine);

        // Gold divider
        doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(1.5f))
                .setStrokeColor(BRAND_GOLD).setMarginBottom(14));
    }

    private void addStatusBanner(Document doc, PdfFont bold, TicketStatus status) {
        String msg  = switch (status) {
            case VOID      -> "⚠  THIS TICKET HAS BEEN VOIDED";
            case CANCELLED -> "⚠  THIS TICKET HAS BEEN CANCELLED";
            case REISSUED  -> "ℹ  THIS TICKET HAS BEEN REISSUED — Please use the latest ticket";
            default        -> "";
        };
        DeviceRgb color = (status == TicketStatus.REISSUED)
                ? new DeviceRgb(255, 140, 0)
                : new DeviceRgb(200, 0, 0);

        Paragraph banner = new Paragraph(msg)
                .setFont(bold).setFontSize(11)
                .setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(color)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(8)
                .setMarginBottom(12);
        doc.add(banner);
    }

    private void addBodyTable(Document doc, PdfFont bold, PdfFont normal, Ticket ticket)
            throws IOException {

        // Two-column layout: left = details, right = QR code
        Table outer = new Table(UnitValue.createPercentArray(new float[]{62, 38}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(14);

        // ── Left cell: details ───────────────────────────────────────────────
        Cell leftCell = new Cell().setBorder(new SolidBorder(BRAND_BLUE, 1))
                .setBackgroundColor(LIGHT_GRAY).setPadding(12);

        // Passenger section
        leftCell.add(sectionTitle("PASSENGER INFORMATION", bold));
        leftCell.add(detailRow("Name",             ticket.getPassenger().getFullName(),            bold, normal));
        leftCell.add(detailRow("Passport",         ticket.getPassenger().getMaskedPassportNumber(), bold, normal));

        leftCell.add(new Paragraph("\n"));

        // Journey section
        leftCell.add(sectionTitle("JOURNEY DETAILS", bold));
        leftCell.add(detailRow("Flight",           ticket.getFlight().getFlightNumber(),           bold, normal));
        leftCell.add(detailRow("From",
                ticket.getFlight().getOriginCode() + "  " + nvl(ticket.getFlight().getOriginName()),
                bold, normal));
        leftCell.add(detailRow("To",
                ticket.getFlight().getDestinationCode() + "  " + nvl(ticket.getFlight().getDestinationName()),
                bold, normal));
        leftCell.add(detailRow("Departure",        ticket.getFlight().getDepartureTime().format(DT_FMT), bold, normal));
        leftCell.add(detailRow("Arrival",          ticket.getFlight().getArrivalTime().format(DT_FMT),   bold, normal));
        leftCell.add(detailRow("Seat",             ticket.getSeat().getSeatNumber(),               bold, normal));
        leftCell.add(detailRow("Cabin Class",      nvl(ticket.getSeat().getCabinClass()),          bold, normal));

        leftCell.add(new Paragraph("\n"));

        // Ticket info section
        leftCell.add(sectionTitle("TICKET INFORMATION", bold));
        leftCell.add(detailRow("Ticket Number",    ticket.getTicketNumber(),                       bold, normal));
        leftCell.add(detailRow("Booking Ref",      ticket.getBooking().getBookingReference(),      bold, normal));
        leftCell.add(detailRow("Version",          String.valueOf(ticket.getTicketVersion()),      bold, normal));
        leftCell.add(detailRow("Issued",           ticket.getIssuedAt().format(DT_FMT),           bold, normal));
        leftCell.add(detailRow("Status",           ticket.getTicketStatus().name(),               bold, normal));

        outer.addCell(leftCell);

        // ── Right cell: QR code ──────────────────────────────────────────────
        Cell rightCell = new Cell()
                .setBorder(new SolidBorder(BRAND_BLUE, 1))
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(12);

        rightCell.add(new Paragraph("SCAN TO VALIDATE")
                .setFont(bold).setFontSize(9)
                .setFontColor(BRAND_BLUE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(8));

        // Embed QR image
        if (ticket.getQrCodeData() != null) {
            try {
                byte[] qrBytes = Base64.getDecoder().decode(ticket.getQrCodeData());
                Image qrImage  = new Image(ImageDataFactory.create(qrBytes))
                        .setWidth(140)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                rightCell.add(qrImage);
            } catch (Exception e) {
                rightCell.add(new Paragraph("[QR code unavailable]")
                        .setFont(normal).setFontSize(8).setFontColor(ColorConstants.RED));
            }
        }

        rightCell.add(new Paragraph(ticket.getQrCodeToken() != null
                ? ticket.getQrCodeToken().substring(0, Math.min(16, ticket.getQrCodeToken().length())) + "..."
                : "")
                .setFont(normal).setFontSize(7)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(6));

        outer.addCell(rightCell);
        doc.add(outer);
    }

    private void addFooter(Document doc, PdfFont normal) {
        doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.5f))
                .setStrokeColor(ColorConstants.LIGHT_GRAY).setMarginBottom(6));

        doc.add(new Paragraph(
                "This is an electronic ticket. Please carry a printed or digital copy at the time of travel.\n" +
                "This ticket is non-transferable. For assistance contact support@skylanka.lk")
                .setFont(normal).setFontSize(8)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER));
    }

    // ── micro helpers ────────────────────────────────────────────────────────

    private Paragraph sectionTitle(String title, PdfFont bold) {
        return new Paragraph(title)
                .setFont(bold).setFontSize(9)
                .setFontColor(BRAND_BLUE)
                .setMarginBottom(4);
    }

    private Paragraph detailRow(String label, String value, PdfFont bold, PdfFont normal) {
        return new Paragraph()
                .add(new Text(label + ": ").setFont(bold).setFontSize(9))
                .add(new Text(value).setFont(normal).setFontSize(10))
                .setMarginBottom(3);
    }

    private String nvl(String s) {
        return (s != null && !s.isBlank()) ? s : "-";
    }
}
