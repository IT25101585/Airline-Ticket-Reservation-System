package com.skylanka.air.shared.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin, top-down drawing surface over PDFBox used by the ticket and receipt renderers.
 *
 * <p>All Y coordinates are measured from the TOP of the page (like HTML/CSS), which keeps layout
 * code readable. Colours are plain {@code 0xRRGGBB} ints. This is the only class in the redesigned
 * documents that talks to PDFBox directly, so the layout classes stay library-agnostic.
 */
public final class PdfCanvas {

    public enum Face { REGULAR, BOLD }

    public static final float PAGE_W = 595.28f;
    public static final float PAGE_H = 841.89f;

    private final PDDocument doc = new PDDocument();
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDPageContentStream cs;
    private int pageNo = 0;

    public PdfCanvas() throws IOException {
        newPage();
    }

    public int pageNumber() {
        return pageNo;
    }

    public void newPage() throws IOException {
        if (cs != null) cs.close();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        cs = new PDPageContentStream(doc, page);
        pageNo++;
    }

    public byte[] finish() throws IOException {
        cs.close();
        cs = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        doc.close();
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- text

    /** Standard-14 fonts only cover Latin text: strip accents, replace anything else with '?'. */
    public static String clean(String value) {
        if (value == null) return "";
        String s = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.replaceAll("[\\r\\n\\t]+", " ");
        return s.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private PDType1Font font(Face f) {
        return f == Face.BOLD ? bold : regular;
    }

    public float width(String s, Face f, float size) throws IOException {
        return font(f).getStringWidth(clean(s)) / 1000f * size;
    }

    /** Largest font size in [min, max] at which {@code s} fits in {@code maxW} (returns min if none does). */
    public float fitSize(String s, Face f, float max, float min, float maxW) throws IOException {
        float size = max;
        while (size > min && width(s, f, size) > maxW) size -= 0.5f;
        return size;
    }

    /** Word-wraps {@code s} to {@code maxW}; over-long words (IDs, codes) are broken by character. */
    public List<String> wrap(String s, Face f, float size, float maxW) throws IOException {
        List<String> lines = new ArrayList<>();
        String text = clean(s).trim();
        if (text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" +")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (width(candidate, f, size) <= maxW) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            // Word alone is too wide: hard-break it.
            String rest = word;
            while (width(rest, f, size) > maxW && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && width(rest.substring(0, cut), f, size) > maxW) cut--;
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /** Draws text with its baseline at {@code baseline} (distance from page top). */
    public void text(String s, float x, float baseline, Face f, float size, int rgb) throws IOException {
        String t = clean(s);
        if (t.isEmpty()) return;
        cs.beginText();
        cs.setFont(font(f), size);
        fillColor(rgb);
        cs.newLineAtOffset(x, PAGE_H - baseline);
        cs.showText(t);
        cs.endText();
    }

    public void textRight(String s, float right, float baseline, Face f, float size, int rgb) throws IOException {
        text(s, right - width(s, f, size), baseline, f, size, rgb);
    }

    public void textCenter(String s, float cx, float baseline, Face f, float size, int rgb) throws IOException {
        text(s, cx - width(s, f, size) / 2f, baseline, f, size, rgb);
    }

    // ---------------------------------------------------------------- shapes

    private void fillColor(int rgb) throws IOException {
        cs.setNonStrokingColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    private void strokeColor(int rgb) throws IOException {
        cs.setStrokingColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    public void fillRect(float x, float top, float w, float h, int rgb) throws IOException {
        fillColor(rgb);
        cs.addRect(x, PAGE_H - top - h, w, h);
        cs.fill();
    }

    public void strokeRect(float x, float top, float w, float h, float lineWidth, int rgb) throws IOException {
        strokeColor(rgb);
        cs.setLineWidth(lineWidth);
        cs.addRect(x, PAGE_H - top - h, w, h);
        cs.stroke();
    }

    /** Straight line; pass a dash pattern (e.g. {3, 3}) or null for solid. */
    public void line(float x1, float y1, float x2, float y2, float lineWidth, int rgb, float[] dash) throws IOException {
        strokeColor(rgb);
        cs.setLineWidth(lineWidth);
        if (dash != null) cs.setLineDashPattern(dash, 0);
        cs.moveTo(x1, PAGE_H - y1);
        cs.lineTo(x2, PAGE_H - y2);
        cs.stroke();
        if (dash != null) cs.setLineDashPattern(new float[0], 0);
    }

    public void polygon(float[] xs, float[] tops, int rgb) throws IOException {
        fillColor(rgb);
        cs.moveTo(xs[0], PAGE_H - tops[0]);
        for (int i = 1; i < xs.length; i++) cs.lineTo(xs[i], PAGE_H - tops[i]);
        cs.closePath();
        cs.fill();
    }

    // ---------------------------------------------------------------- QR

    /**
     * Draws the ticket QR exactly as the previous ticket did: white square including a 4-module
     * quiet zone, every dark module as its own vector rectangle, thin outer border.
     * {@code (x, top)} is the top-left corner and {@code size} the full width/height in points.
     */
    public void qr(String data, float x, float top, float size) throws IOException {
        boolean[][] modules = QrCode.encode(data);
        final int quiet = 4;
        final float cell = size / (modules.length + quiet * 2);
        final float bottom = PAGE_H - top - size;

        fillColor(0xFFFFFF);
        cs.addRect(x, bottom, size, size);
        cs.fill();

        fillColor(0x000000);
        for (int row = 0; row < modules.length; row++) {
            for (int col = 0; col < modules.length; col++) {
                if (!modules[row][col]) continue;
                cs.addRect(x + (col + quiet) * cell, bottom + size - (row + quiet + 1) * cell, cell + 0.02f, cell + 0.02f);
                cs.fill();
            }
        }

        strokeColor(0x404040);
        cs.setLineWidth(0.6f);
        cs.addRect(x, bottom, size, size);
        cs.stroke();
    }
}
