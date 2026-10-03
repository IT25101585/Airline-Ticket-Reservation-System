package com.skylanka.air.shared.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builder for the simple single-page, title-plus-line-items PDF documents this app
 * generates: payment receipts, e-tickets, and boarding passes.
 *
 * Before this class existed, those three documents were each built by hand-rolling
 * the same ~30 lines of PDFBox boilerplate (create document, create page, open a
 * content stream, position text, draw a bold title, loop over lines at a fixed
 * offset, strip non-printable characters, save to a byte array) directly inside
 * {@code PaymentService.receiptPdf}, {@code TicketService.pdf} and
 * {@code OperationsController.boardingPass} — including three separate copies of
 * the same sanitization helper. None of the three needed to know about that
 * mechanics; they only differ in page size, layout constants, and the actual
 * content lines.
 *
 * This builder assembles the document step by step (page size, start position,
 * fonts, title, body lines) while keeping the PDFBox lifecycle and low-level layout
 * math private, and {@link #build()} produces the finished, immutable PDF as a
 * {@code byte[]}. Each caller only states *what* the document should contain.
 */
public final class PdfDocumentBuilder {

    private PDRectangle pageSize = PDRectangle.A4;
    private float startX = 60f;
    private float startY = 760f;
    private float titleFontSize = 20f;
    private float bodyFontSize = 12f;
    private float lineSpacing = 26f;
    private String title;
    private final List<String> lines = new ArrayList<>();
    private String qrData;
    private float qrX;
    private float qrY;
    private float qrSize;

    private PdfDocumentBuilder() {
    }

    public static PdfDocumentBuilder create() {
        return new PdfDocumentBuilder();
    }

    public PdfDocumentBuilder pageSize(PDRectangle pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    /** Where the title is drawn, and where the first body line starts from. */
    public PdfDocumentBuilder startPosition(float x, float y) {
        this.startX = x;
        this.startY = y;
        return this;
    }

    public PdfDocumentBuilder fontSizes(float titleFontSize, float bodyFontSize) {
        this.titleFontSize = titleFontSize;
        this.bodyFontSize = bodyFontSize;
        return this;
    }

    public PdfDocumentBuilder lineSpacing(float lineSpacing) {
        this.lineSpacing = lineSpacing;
        return this;
    }

    public PdfDocumentBuilder title(String title) {
        this.title = title;
        return this;
    }

    public PdfDocumentBuilder line(String line) {
        this.lines.add(line);
        return this;
    }

    public PdfDocumentBuilder lines(String... lines) {
        this.lines.addAll(Arrays.asList(lines));
        return this;
    }

    /**
     * Draws a QR code encoding {@code data}. {@code (x, y)} is the bottom-left corner of the code
     * (including its 4-module quiet zone) and {@code size} its width/height in PDF points.
     */
    public PdfDocumentBuilder qrCode(String data, float x, float y, float size) {
        this.qrData = data;
        this.qrX = x;
        this.qrY = y;
        this.qrSize = size;
        return this;
    }

    public PdfDocumentBuilder blankLine() {
        this.lines.add("");
        return this;
    }

    /**
     * Assembles the document and returns the finished PDF bytes. Every line
     * (title included) is run through the same non-printable-character
     * sanitization before it's drawn, so callers never need their own copy of
     * that logic.
     */
    public byte[] build() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);

            try (PDPageContentStream c = new PDPageContentStream(doc, page)) {
                c.beginText();
                c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), titleFontSize);
                c.newLineAtOffset(startX, startY);
                c.showText(sanitize(title));

                c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), bodyFontSize);
                for (String line : lines) {
                    c.newLineAtOffset(0, -lineSpacing);
                    c.showText(sanitize(line));
                }

                c.endText();

                if (qrData != null && !qrData.isBlank() && qrSize > 0) {
                    drawQrCode(c);
                }
            }

            doc.save(out);
        }

        return out.toByteArray();
    }

    /**
     * Draws a high-contrast QR code directly as PDF vector rectangles. A white
     * background and quiet zone are painted first so the code stays visible even
     * when the surrounding ticket design later gains backgrounds or graphics.
     */
    private void drawQrCode(PDPageContentStream c) throws IOException {
        boolean[][] modules = QrCode.encode(qrData);
        final int quietZone = 4;
        final int moduleCount = modules.length + quietZone * 2;
        final float cell = qrSize / moduleCount;

        // Solid white square behind the QR, including the required quiet zone.
        c.setNonStrokingColor(1f, 1f, 1f);
        c.addRect(qrX, qrY, qrSize, qrSize);
        c.fill();

        // Draw every dark QR module explicitly. Filling each module immediately
        // avoids path-size/rendering issues in some PDF viewers and print drivers.
        c.setNonStrokingColor(0f, 0f, 0f);
        for (int row = 0; row < modules.length; row++) {
            for (int col = 0; col < modules.length; col++) {
                if (!modules[row][col]) continue;
                float x = qrX + (col + quietZone) * cell;
                float y = qrY + qrSize - (row + quietZone + 1) * cell;
                c.addRect(x, y, cell + 0.02f, cell + 0.02f);
                c.fill();
            }
        }

        // Thin border outside the quiet zone so users can clearly see the QR area.
        c.setStrokingColor(0.25f, 0.25f, 0.25f);
        c.setLineWidth(0.6f);
        c.addRect(qrX, qrY, qrSize, qrSize);
        c.stroke();
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
