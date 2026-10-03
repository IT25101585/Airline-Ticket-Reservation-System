package com.skylanka.air.shared.pdf;

import com.skylanka.air.shared.pdf.PdfCanvas.Face;

import java.io.IOException;
import java.util.List;

/**
 * Shared SkyLanka Air page furniture (header band, footer, label/value sections) so the e-ticket and
 * the payment receipt look like one document family. Layout is A4, 40pt side margins, top-down Y.
 */
public final class SkyLankaDoc {

    // Brand palette (from travel-theme.css): navy #0f2b4a, blue #0a9eff. Everything else is neutral
    // so the documents stay legible when printed in grayscale.
    public static final int NAVY = 0x0F2B4A;
    public static final int BLUE = 0x0A9EFF;
    public static final int ON_NAVY_SOFT = 0xBFD6EA;
    public static final int INK = 0x1A222C;
    public static final int MUTED = 0x5A6878;
    public static final int RULE = 0xD3DAE2;
    public static final int PANEL = 0xF2F5F8;
    public static final int WHITE = 0xFFFFFF;

    public static final float M = 40f;
    public static final float CW = PdfCanvas.PAGE_W - 2 * M;
    /** Content must stay above this line; the footer lives below it. */
    public static final float LIMIT = 772f;
    private static final float FOOTER_LINE = 786f;

    public record Row(String label, String value, boolean strong) {
        public static Row of(String label, String value) {
            return new Row(label, value, false);
        }

        public static Row strong(String label, String value) {
            return new Row(label, value, true);
        }
    }

    private final PdfCanvas c;
    private final String docTitle;
    private final String docSubtitle;
    private final List<String> footerNotes;
    private final String footerRef;

    public SkyLankaDoc(String docTitle, String docSubtitle, List<String> footerNotes, String footerRef) throws IOException {
        this.c = new PdfCanvas();
        this.docTitle = docTitle;
        this.docSubtitle = docSubtitle;
        this.footerNotes = footerNotes;
        this.footerRef = footerRef;
        header(false);
    }

    public PdfCanvas canvas() {
        return c;
    }

    /** Y where content starts on the first page (below the full header). */
    public static float firstPageTop() {
        return 92f;
    }

    // ------------------------------------------------------------ page furniture

    private void header(boolean compact) throws IOException {
        float h = compact ? 34f : 70f;
        c.fillRect(0, 0, PdfCanvas.PAGE_W, h, NAVY);
        c.fillRect(0, h, PdfCanvas.PAGE_W, 3f, BLUE);

        float s = compact ? 0.6f : 1f;
        float top = compact ? 9f : 23f;
        // Simple paper-plane mark.
        c.polygon(new float[]{M, M + 24 * s, M + 15 * s, M + 11 * s},
                new float[]{top + 12 * s, top, top + 24 * s, top + 15 * s}, WHITE);
        float tx = M + 32 * s;
        float size = compact ? 14f : 22f;
        float base = compact ? 23f : 45f;
        c.text("SKYLANKA", tx, base, Face.BOLD, size, WHITE);
        c.text(" AIR", tx + c.width("SKYLANKA", Face.BOLD, size), base, Face.BOLD, size, 0x7CC8FF);

        float right = PdfCanvas.PAGE_W - M;
        if (compact) {
            c.textRight(docTitle, right, 22, Face.BOLD, 9, WHITE);
        } else {
            c.textRight(docTitle, right, 33, Face.BOLD, 14, WHITE);
            if (docSubtitle != null && !docSubtitle.isBlank()) {
                c.textRight(docSubtitle, right, 49, Face.REGULAR, 8, ON_NAVY_SOFT);
            }
        }
    }

    private void footer() throws IOException {
        c.line(M, FOOTER_LINE, M + CW, FOOTER_LINE, 0.6f, RULE, null);
        float y = FOOTER_LINE + 12;
        String right = (footerRef == null || footerRef.isBlank() ? "" : footerRef + "  |  ") + "Page " + c.pageNumber();
        float rightW = c.width(right, Face.REGULAR, 7.5f);
        for (String note : footerNotes) {
            float maxW = CW - rightW - 12;
            float size = c.fitSize(note, Face.REGULAR, 7.5f, 6f, maxW);
            c.text(note, M, y, Face.REGULAR, size, MUTED);
            y += 10f;
        }
        c.textRight(right, M + CW, FOOTER_LINE + 12, Face.REGULAR, 7.5f, MUTED);
    }

    /** Ends the current page (draws its footer) and starts a fresh one with a compact header. */
    public float pageBreak() throws IOException {
        footer();
        c.newPage();
        header(true);
        return 54f;
    }

    public byte[] finish() throws IOException {
        footer();
        return c.finish();
    }

    // ------------------------------------------------------------ label / value sections

    private static final float LABEL_SIZE = 7f;
    private static final float VALUE_SIZE = 9.5f;
    private static final float LINE_H = 11.5f;
    private static final float ROW_PAD = 7f;
    private static final float TITLE_H = 18f;
    private static final float GAP = 12f;

    private float rowHeight(Row r, float valueW) throws IOException {
        Face f = r.strong() ? Face.BOLD : Face.REGULAR;
        return c.wrap(r.value(), f, VALUE_SIZE, valueW).size() * LINE_H + ROW_PAD;
    }

    /** Height a section would occupy (title + rows + trailing gap) at width {@code w}. */
    public float measureSection(List<Row> rows, float w, float labelW) throws IOException {
        float h = TITLE_H;
        for (Row r : rows) h += rowHeight(r, w - labelW - 6);
        return h + GAP;
    }

    /**
     * Draws a titled section and returns the Y below it. Rows never split mid-row; if the section
     * runs past {@link #LIMIT} it continues on a new page under a "(continued)" title.
     */
    public float section(String title, List<Row> rows, float x, float y, float w, float labelW) throws IOException {
        y = sectionTitle(title, x, y, w);
        float valueW = w - labelW - 6;
        for (Row r : rows) {
            Face f = r.strong() ? Face.BOLD : Face.REGULAR;
            List<String> lines = c.wrap(r.value(), f, VALUE_SIZE, valueW);
            float h = lines.size() * LINE_H + ROW_PAD;
            if (y + h > LIMIT) {
                y = pageBreak();
                y = sectionTitle(title + " (CONTINUED)", x, y, w);
            }
            String label = r.label().toUpperCase();
            // Shrink long labels so they can never run into the value column.
            float labelSize = c.fitSize(label, Face.REGULAR, LABEL_SIZE, 5f, labelW - 8f);
            c.text(label, x, y + 9.5f, Face.REGULAR, labelSize, MUTED);
            float ly = y + 10f;
            for (String line : lines) {
                c.text(line, x + labelW, ly, f, VALUE_SIZE, INK);
                ly += LINE_H;
            }
            y += h;
            c.line(x, y - 1.5f, x + w, y - 1.5f, 0.4f, RULE, null);
        }
        return y + GAP;
    }

    private float sectionTitle(String title, float x, float y, float w) throws IOException {
        c.text(title, x, y + 9f, Face.BOLD, 8.5f, NAVY);
        c.line(x, y + 14f, x + w, y + 14f, 1.1f, NAVY, null);
        return y + TITLE_H;
    }

    /** Small caption label (caps) above a value, used in the boarding-pass grid. */
    public void caption(String label, float x, float baseline) throws IOException {
        c.text(label.toUpperCase(), x, baseline, Face.REGULAR, LABEL_SIZE, MUTED);
    }
}
