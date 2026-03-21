package com.raez.finance.service;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ExportService — CSV and PDF export.
 *
 * exportMergedReport() accepts rows with special markers for rich multi-page output:
 *   "__COVER__"       [1]=title [2]=subtitle [3]=date
 *   "__SECTION__"     [1]=title [2]=subtitle
 *   "__KPI__"         [1..8]=label/value pairs (up to 4 KPI boxes)
 *   "__BARCHART__"    [1]=chartTitle [2,4,6...]=labels [3,5,7...]=numeric values
 *   "__TABLEHEADER__" [1..n]=column names
 *   "__PAGEBREAK__"   forces new page
 *   empty row {}      vertical spacer
 *   normal row        table data cells
 */
public class ExportService {

    // ── Colour constants (PDFBox float 0–1) ──────────────────────────────
    private static final float[] C_NAVY       = {0.118f, 0.161f, 0.224f};  // #1E2939
    private static final float[] C_NAVY_LIGHT = {0.173f, 0.224f, 0.306f};  // #2C3950
    private static final float[] C_GREEN      = {0.063f, 0.725f, 0.506f};  // #10B981
    private static final float[] C_AMBER      = {0.961f, 0.620f, 0.043f};  // #F59E0B
    private static final float[] C_PAGE_BG    = {0.976f, 0.980f, 0.984f};  // #F9FAFB
    private static final float[] C_BORDER     = {0.898f, 0.906f, 0.922f};  // #E5E7EB
    private static final float[] C_MUTED      = {0.420f, 0.447f, 0.502f};  // #6B7280
    private static final float[] C_WHITE      = {1f, 1f, 1f};
    private static final float[] C_BLACK      = {0f, 0f, 0f};
    private static final float[] C_ROW_ALT    = {0.973f, 0.973f, 0.980f};  // subtle stripe

    // ── Geometry ─────────────────────────────────────────────────────────
    private static final float PAGE_W  = PDRectangle.A4.getWidth();
    private static final float PAGE_H  = PDRectangle.A4.getHeight();
    private static final float MARGIN  = 48f;
    private static final float ROW_H   = 18f;
    private static final float FOOTER_H = 28f;
    private static final float BODY_BOTTOM = MARGIN + FOOTER_H + ROW_H;

    // =====================================================================
    //  PUBLIC API — unchanged signatures
    // =====================================================================

    /** Export TableView to CSV (generic, uses cell data). */
    public static void exportToCSV(TableView<?> table, File file) throws Exception {
        List<String> headers = new ArrayList<>();
        for (TableColumn<?, ?> col : table.getColumns()) {
            String title = col.getText() != null ? col.getText() : col.getId();
            headers.add(escapeCsv(title));
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers)).append("\n");
        for (Object row : table.getItems()) {
            List<String> cells = new ArrayList<>();
            for (TableColumn<?, ?> col : table.getColumns()) {
                @SuppressWarnings("unchecked")
                TableColumn<Object, ?> c = (TableColumn<Object, ?>) col;
                Object val = c.getCellData(row);
                cells.add(escapeCsv(val != null ? val.toString() : ""));
            }
            sb.append(String.join(",", cells)).append("\n");
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
    }

    /** Export raw List<String[]> to CSV. */
    public static void exportRowsToCSV(List<String[]> data, File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String[] row : data) {
            List<String> escaped = new ArrayList<>();
            for (String cell : row) escaped.add(escapeCsv(cell != null ? cell : ""));
            sb.append(String.join(",", escaped)).append("\n");
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
    }

    /** Export raw rows to a professional single-section PDF. */
    public static void exportRowsToPDF(String title, List<String[]> data, File file) throws Exception {
        exportRowsToPDFProfessional(title, null, data, file);
    }

    /** Export TableView to PDF (generic). */
    public static void exportToPDF(TableView<?> table, String title, File file) throws Exception {
        List<String> headers = new ArrayList<>();
        for (TableColumn<?, ?> col : table.getColumns()) {
            String t = col.getText() != null ? col.getText() : col.getId();
            headers.add(t != null ? t : "");
        }
        List<String[]> data = new ArrayList<>();
        data.add(headers.toArray(new String[0]));
        for (Object row : table.getItems()) {
            List<String> cells = new ArrayList<>();
            for (TableColumn<?, ?> col : table.getColumns()) {
                @SuppressWarnings("unchecked")
                TableColumn<Object, ?> c = (TableColumn<Object, ?>) col;
                Object val = c.getCellData(row);
                cells.add(val != null ? val.toString() : "");
            }
            data.add(cells.toArray(new String[0]));
        }
        List<String> summary = new ArrayList<>();
        summary.add("Total rows: " + (data.size() - 1));
        exportRowsToPDFProfessional(title != null ? title : "Report", summary, data, file);
    }

    // =====================================================================
    //  MERGED REPORT  — rich multi-section, 5+ pages, cover + charts
    // =====================================================================

    /**
     * Produces a rich multi-section PDF from structured data rows.
     * Rows with special markers are rendered as:
     *   __COVER__       → branded cover page
     *   __SECTION__     → navy section header band
     *   __KPI__         → up to 4 KPI boxes side by side
     *   __BARCHART__    → horizontal bar chart
     *   __TABLEHEADER__ → bold shaded table header
     *   __PAGEBREAK__   → hard page break
     *   {}              → vertical spacer
     *   normal          → alternating-row table data
     */
    public static void exportMergedReport(String title, List<String[]> data, File file) throws Exception {
        if (data == null) data = new ArrayList<>();

        GlobalSettingsService gs  = GlobalSettingsService.getInstance();
        String company     = nvl(gs.getCompanyName(),    "RAEZ Finance");
        String address     = nvl(gs.getCompanyAddress(), "");
        String vatText     = String.format("VAT rate: %d%%",
                             (int) Math.round(gs.getDefaultVatPercent()));
        String genDate     = LocalDate.now()
                             .format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));

        PDType1Font fontReg  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        try (PDDocument doc = new PDDocument()) {

            // ── State ────────────────────────────────────────────────────
            PDPage            page      = newPage(doc);
            PDPageContentStream cs      = new PDPageContentStream(doc, page);
            float             y        = PAGE_H - MARGIN;
            int               pageNum  = 1;
            boolean           inTable  = false;
            int               tableCols = 0;
            float             colW     = 0;
            int               dataRowIdx = 0; // alternating row colour

            for (String[] row : data) {

                // ── Empty row = spacer ────────────────────────────────────
                if (row == null || row.length == 0) {
                    y -= ROW_H * 0.6f;
                    continue;
                }

                String marker = row[0];

                // ── Cover page ───────────────────────────────────────────
                if ("__COVER__".equals(marker)) {
                    String rTitle    = row.length > 1 ? row[1] : title;
                    String rSubtitle = row.length > 2 ? row[2] : company;
                    String rDate     = row.length > 3 ? row[3] : genDate;
                    drawCoverPage(cs, fontBold, fontReg, rTitle, rSubtitle, company, address, rDate);
                    cs = nextPage(cs, doc, fontReg, PAGE_W, MARGIN, pageNum, vatText);
                    pageNum++;
                    y = PAGE_H - MARGIN;
                    inTable = false;
                    continue;
                }

                // ── Hard page break ──────────────────────────────────────
                if ("__PAGEBREAK__".equals(marker)) {
                    cs = nextPage(cs, doc, fontReg, PAGE_W, MARGIN, pageNum, vatText);
                    pageNum++;
                    y = PAGE_H - MARGIN;
                    inTable = false;
                    dataRowIdx = 0;
                    continue;
                }

                // ── Section header ───────────────────────────────────────
                if ("__SECTION__".equals(marker)) {
                    inTable   = false;
                    dataRowIdx = 0;
                    // Need a little room — start new page if low
                    if (y < BODY_BOTTOM + 80) {
                        cs = nextPage(cs, doc, fontReg, PAGE_W, MARGIN, pageNum, vatText);
                        pageNum++;
                        y = PAGE_H - MARGIN;
                    }
                    String hTitle    = row.length > 1 ? row[1] : "";
                    String hSubtitle = row.length > 2 ? row[2] : "";
                    y = drawSectionHeader(cs, fontBold, fontReg, hTitle, hSubtitle, y);
                    continue;
                }

                // ── KPI boxes ────────────────────────────────────────────
                if ("__KPI__".equals(marker)) {
                    if (y < BODY_BOTTOM + 60) {
                        cs = nextPage(cs, doc, fontReg, PAGE_W, MARGIN, pageNum, vatText);
                        pageNum++;
                        y = PAGE_H - MARGIN;
                    }
                    y = drawKpiBoxes(cs, fontBold, fontReg, row, y);
                    continue;
                }

                // ── Bar chart ────────────────────────────────────────────
                if ("__BARCHART__".equals(marker)) {
                    // Estimate height needed: title + bars (each ~16px) + padding
                    int numBars = (row.length - 2) / 2;
                    float needed = 30 + numBars * 20f + 20;
                    if (y < BODY_BOTTOM + needed) {
                        cs = nextPage(cs, doc, fontReg, PAGE_W, MARGIN, pageNum, vatText);
                        pageNum++;
                        y = PAGE_H - MARGIN;
                    }
                    y = drawBarChart(cs, fontBold, fontReg, row, y);
                    continue;
                }

                // ── Table header row ─────────────────────────────────────
                if ("__TABLEHEADER__".equals(marker)) {
                    inTable    = true;
                    dataRowIdx = 0;
                    tableCols  = row.length - 1;
                    colW       = (PAGE_W - 2 * MARGIN) / Math.max(1, tableCols);
                    if (y < BODY_BOTTOM + ROW_H * 3) {
                        cs = nextPage(cs, doc, fontReg, PAGE_W, MARGIN, pageNum, vatText);
                        pageNum++;
                        y = PAGE_H - MARGIN;
                    }
                    String[] headers = java.util.Arrays.copyOfRange(row, 1, row.length);
                    drawTableRow(cs, fontBold, fontReg, headers, y, colW, ROW_H, true, 0, tableCols);
                    y -= ROW_H;
                    continue;
                }

                // ── Normal data row ──────────────────────────────────────
                if (y < BODY_BOTTOM) {
                    cs = nextPage(cs, doc, fontReg, PAGE_W, MARGIN, pageNum, vatText);
                    pageNum++;
                    y = PAGE_H - MARGIN;
                    // Repeat last table header if in table
                    if (inTable) {
                        y -= ROW_H * 0.5f;
                    }
                }
                if (inTable && tableCols > 0) {
                    // Ensure row has correct column count
                    String[] cells = new String[tableCols];
                    for (int c = 0; c < tableCols; c++)
                        cells[c] = c < row.length ? row[c] : "";
                    drawTableRow(cs, fontBold, fontReg, cells, y, colW, ROW_H, false, dataRowIdx, tableCols);
                    y -= ROW_H;
                    dataRowIdx++;
                } else if (!inTable) {
                    // Fallback: render as simple 2-col table if no header was seen
                    if (tableCols == 0 && row.length > 0) {
                        tableCols  = row.length;
                        colW       = (PAGE_W - 2 * MARGIN) / Math.max(1, tableCols);
                    }
                    String[] cells = new String[Math.max(tableCols, row.length)];
                    for (int c = 0; c < cells.length; c++)
                        cells[c] = c < row.length ? row[c] : "";
                    drawTableRow(cs, fontBold, fontReg, cells, y, colW, ROW_H, false, dataRowIdx, cells.length);
                    y -= ROW_H;
                    dataRowIdx++;
                }
            }

            drawPageFooter(cs, fontReg, PAGE_W, MARGIN, pageNum, vatText);
            cs.close();
            doc.save(file);
        }
    }

    // =====================================================================
    //  COVER PAGE
    // =====================================================================

    private static void drawCoverPage(PDPageContentStream cs,
            PDType1Font fontBold, PDType1Font fontReg,
            String reportTitle, String subtitle,
            String company, String address, String date) throws Exception {

        float w = PAGE_W;
        float h = PAGE_H;

        // ── Page background ───────────────────────────────────────────────
        setFill(cs, C_PAGE_BG);
        cs.addRect(0, 0, w, h);
        cs.fill();

        // ── Top navy bar (full width, top 90px) ───────────────────────────
        setFill(cs, C_NAVY);
        cs.addRect(0, h - 90, w, 90);
        cs.fill();

        // Company name in top bar (white)
        float topTextY = h - 55;
        drawText(cs, fontBold, 18, C_WHITE, MARGIN, topTextY,
                 trimForPdf(company, 50));
        drawText(cs, fontReg, 10, new float[]{0.8f, 0.85f, 0.9f}, MARGIN, topTextY - 18,
                 trimForPdf(address, 70));

        // ── Centre block ──────────────────────────────────────────────────
        float blockTop = h - 200;

        // Accent line (green)
        setFill(cs, C_GREEN);
        cs.addRect(MARGIN, blockTop, 60, 4);
        cs.fill();

        // Report title (large)
        drawText(cs, fontBold, 26, C_NAVY, MARGIN, blockTop - 40,
                 trimForPdf(reportTitle, 55));

        // Subtitle
        drawText(cs, fontReg, 13, C_MUTED, MARGIN, blockTop - 66,
                 trimForPdf(subtitle, 80));

        // ── Divider ───────────────────────────────────────────────────────
        setStroke(cs, C_BORDER);
        cs.setLineWidth(0.7f);
        cs.moveTo(MARGIN, blockTop - 92);
        cs.lineTo(w - MARGIN, blockTop - 92);
        cs.stroke();

        // ── Meta info ─────────────────────────────────────────────────────
        float metaY = blockTop - 116;
        drawText(cs, fontBold, 10, C_MUTED, MARGIN, metaY, "GENERATED");
        drawText(cs, fontReg,  12, C_NAVY,  MARGIN, metaY - 16, date);

        drawText(cs, fontBold, 10, C_MUTED, MARGIN + 200, metaY, "CLASSIFICATION");
        drawText(cs, fontReg,  12, C_NAVY,  MARGIN + 200, metaY - 16, "CONFIDENTIAL");

        // ── Bottom navy strip ─────────────────────────────────────────────
        setFill(cs, C_NAVY);
        cs.addRect(0, 0, w, 60);
        cs.fill();
        drawText(cs, fontReg, 9, new float[]{0.6f, 0.65f, 0.7f}, MARGIN, 22,
                 "RAEZ Finance Reporting System  ·  This document is confidential");
    }

    // =====================================================================
    //  SECTION HEADER
    // =====================================================================

    private static float drawSectionHeader(PDPageContentStream cs,
            PDType1Font fontBold, PDType1Font fontReg,
            String sectionTitle, String subtitle, float y) throws Exception {

        float bannerH = subtitle != null && !subtitle.isBlank() ? 44f : 32f;
        float bannerY = y - bannerH;

        // Navy banner
        setFill(cs, C_NAVY);
        cs.addRect(MARGIN - 4, bannerY, PAGE_W - 2 * MARGIN + 8, bannerH);
        cs.fill();

        // White title text
        drawText(cs, fontBold, 12, C_WHITE, MARGIN + 4, bannerY + bannerH - 16,
                 trimForPdf(sectionTitle, 70));

        // Lighter subtitle
        if (subtitle != null && !subtitle.isBlank()) {
            drawText(cs, fontReg, 9, new float[]{0.75f, 0.8f, 0.85f},
                     MARGIN + 4, bannerY + 8,
                     trimForPdf(subtitle, 80));
        }

        return bannerY - 12;
    }

    // =====================================================================
    //  KPI BOXES
    // =====================================================================

    private static float drawKpiBoxes(PDPageContentStream cs,
            PDType1Font fontBold, PDType1Font fontReg,
            String[] row, float y) throws Exception {

        // row: [0]=marker [1]=label1 [2]=val1 [3]=label2 [4]=val2 ...
        int numKpis = Math.min(4, (row.length - 1) / 2);
        if (numKpis == 0) return y;

        float boxW = (PAGE_W - 2 * MARGIN - (numKpis - 1) * 12) / numKpis;
        float boxH = 60f;
        float boxY = y - boxH;

        for (int k = 0; k < numKpis; k++) {
            String label = row.length > 1 + k * 2 ? row[1 + k * 2]     : "";
            String value = row.length > 2 + k * 2 ? row[2 + k * 2]     : "—";
            float bx = MARGIN + k * (boxW + 12);

            // Box background
            setFill(cs, C_WHITE);
            cs.addRect(bx, boxY, boxW, boxH);
            cs.fill();

            // Box border
            setStroke(cs, C_BORDER);
            cs.setLineWidth(0.6f);
            cs.addRect(bx, boxY, boxW, boxH);
            cs.stroke();

            // Accent top bar (green for first, navy for rest)
            setFill(cs, k == 0 ? C_GREEN : C_NAVY);
            cs.addRect(bx, boxY + boxH - 4, boxW, 4);
            cs.fill();

            // Label (muted, small caps)
            drawText(cs, fontBold, 8, C_MUTED, bx + 10, boxY + boxH - 18,
                     trimForPdf(label.toUpperCase(), 22));

            // Value (large, bold, navy)
            drawText(cs, fontBold, 15, C_NAVY, bx + 10, boxY + 14,
                     trimForPdf(value, 18));
        }

        return boxY - 16;
    }

    // =====================================================================
    //  BAR CHART
    // =====================================================================

    private static float drawBarChart(PDPageContentStream cs,
            PDType1Font fontBold, PDType1Font fontReg,
            String[] row, float y) throws Exception {

        // row: [0]=marker [1]=chartTitle [2]=label1 [3]=val1 [4]=label2 [5]=val2 ...
        String chartTitle = row.length > 1 ? row[1] : "Chart";

        // Parse labels and values
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int i = 2; i + 1 < row.length; i += 2) {
            labels.add(row[i]);
            try { values.add(Double.parseDouble(row[i + 1])); }
            catch (NumberFormatException ex) { values.add(0.0); }
        }
        if (labels.isEmpty()) return y;

        double maxVal = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (maxVal <= 0) maxVal = 1;

        float labelW   = 110f;
        float barMaxW  = PAGE_W - 2 * MARGIN - labelW - 80;
        float barH     = 12f;
        float rowGap   = 18f;

        // Chart title
        drawText(cs, fontBold, 11, C_NAVY, MARGIN, y - 2,
                 trimForPdf(chartTitle, 60));
        y -= 20;

        // Divider line
        setStroke(cs, C_BORDER);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, y);
        cs.lineTo(PAGE_W - MARGIN, y);
        cs.stroke();
        y -= 12;

        // Draw each bar
        for (int i = 0; i < labels.size(); i++) {
            String label = trimForPdf(labels.get(i), 18);
            double val   = values.get(i);
            float barW   = (float) (val / maxVal * barMaxW);
            if (barW < 2) barW = 2;
            float barTop = y - 2;

            // Label
            drawText(cs, fontReg, 9, C_MUTED, MARGIN, barTop,
                     label);

            // Bar background (very light)
            setFill(cs, new float[]{0.94f, 0.95f, 0.97f});
            cs.addRect(MARGIN + labelW, barTop - barH, barMaxW, barH);
            cs.fill();

            // Bar fill (alternating navy / green)
            float[] barColor = (i % 2 == 0) ? C_NAVY : C_GREEN;
            setFill(cs, barColor);
            cs.addRect(MARGIN + labelW, barTop - barH, barW, barH);
            cs.fill();

            // Value text
            String valText = formatBarValue(val);
            drawText(cs, fontReg, 8, C_MUTED,
                     MARGIN + labelW + barMaxW + 6, barTop,
                     valText);

            y -= rowGap;
        }

        // Bottom divider
        setStroke(cs, C_BORDER);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, y);
        cs.lineTo(PAGE_W - MARGIN, y);
        cs.stroke();
        y -= 16;

        return y;
    }

    // =====================================================================
    //  TABLE ROW
    // =====================================================================

    private static void drawTableRow(PDPageContentStream cs,
            PDType1Font fontBold, PDType1Font fontReg,
            String[] cells, float y, float colW, float rowH,
            boolean isHeader, int rowIndex, int numCols) throws Exception {

        float rowBottom = y - rowH;

        // Background
        if (isHeader) {
            setFill(cs, C_NAVY);
        } else if (rowIndex % 2 == 1) {
            setFill(cs, C_ROW_ALT);
        } else {
            setFill(cs, C_WHITE);
        }
        cs.addRect(MARGIN, rowBottom, PAGE_W - 2 * MARGIN, rowH);
        cs.fill();

        // Bottom border line (subtle for data rows)
        if (!isHeader) {
            setStroke(cs, C_BORDER);
            cs.setLineWidth(0.3f);
            cs.moveTo(MARGIN, rowBottom);
            cs.lineTo(PAGE_W - MARGIN, rowBottom);
            cs.stroke();
        }

        // Cell text
        PDType1Font f  = isHeader ? fontBold : fontReg;
        float[]     fc = isHeader ? C_WHITE : C_BLACK;
        int         fs = isHeader ? 9 : 8;
        float      ty  = rowBottom + 4;

        for (int c = 0; c < numCols && c < cells.length; c++) {
            String cell = trimForPdf(cells[c], isHeader ? 24 : 22);
            drawText(cs, f, fs, fc, MARGIN + 4 + c * colW, ty, cell);
        }
    }

    // =====================================================================
    //  PAGE MANAGEMENT HELPERS
    // =====================================================================

    private static PDPage newPage(PDDocument doc) {
        PDPage p = new PDPage(PDRectangle.A4);
        doc.addPage(p);
        return p;
    }

    private static PDPageContentStream nextPage(PDPageContentStream cs,
            PDDocument doc, PDType1Font font,
            float pageW, float margin, int pageNum, String vatText) throws Exception {
        drawPageFooter(cs, font, pageW, margin, pageNum, vatText);
        cs.close();
        PDPage p = new PDPage(PDRectangle.A4);
        doc.addPage(p);
        return new PDPageContentStream(doc, p);
    }

    private static void drawPageFooter(PDPageContentStream cs,
            PDType1Font font, float pageW, float margin,
            int pageNum, String vatText) throws Exception {
        float y = margin - 8;

        // Footer line
        setStroke(cs, C_BORDER);
        cs.setLineWidth(0.5f);
        cs.moveTo(margin, y + 12);
        cs.lineTo(pageW - margin, y + 12);
        cs.stroke();

        drawText(cs, font, 8, C_MUTED, margin, y, vatText);
        drawText(cs, font, 8, C_MUTED, pageW - margin - 40, y,
                 "Page " + pageNum);
    }

    // =====================================================================
    //  PRIMITIVE HELPERS
    // =====================================================================

    private static void drawText(PDPageContentStream cs,
            PDType1Font font, float size,
            float[] color, float x, float y, String text) throws Exception {
        if (text == null || text.isBlank()) return;
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color[0], color[1], color[2]);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        cs.setNonStrokingColor(0f, 0f, 0f); // reset
    }

    private static void setFill(PDPageContentStream cs, float[] c) throws Exception {
        cs.setNonStrokingColor(c[0], c[1], c[2]);
    }

    private static void setStroke(PDPageContentStream cs, float[] c) throws Exception {
        cs.setStrokingColor(c[0], c[1], c[2]);
    }

    private static String trimForPdf(String s, int maxLen) {
        if (s == null) return "";
        s = s.replaceAll("[\\x00-\\x1f]", " ").trim();
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "…" : s;
    }

    private static String formatBarValue(double val) {
        if (val >= 1_000_000) return String.format("£%.1fM", val / 1_000_000);
        if (val >= 1_000)     return String.format("£%.1fk", val / 1_000);
        if (val == Math.floor(val)) return String.format("%d", (long) val);
        return String.format("£%.0f", val);
    }

    private static String nvl(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s : fallback;
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    // =====================================================================
    //  PROFESSIONAL SINGLE-SECTION PDF  (unchanged, used by exportRowsToPDF)
    // =====================================================================

    public static void exportRowsToPDFProfessional(String reportTitle,
            List<String> summaryLines,
            List<String[]> data, File file) throws Exception {

        GlobalSettingsService gs = GlobalSettingsService.getInstance();
        String companyName    = gs.getCompanyName()    != null ? gs.getCompanyName()    : "";
        String companyAddress = gs.getCompanyAddress() != null ? gs.getCompanyAddress() : "";
        String vatRateText    = String.format("VAT rate applied: %s%%",
                                String.valueOf((int) Math.round(gs.getDefaultVatPercent())));
        String generatedDate  = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        if (reportTitle   == null) reportTitle   = "Report";
        if (summaryLines  == null) summaryLines  = new ArrayList<>();
        if (data          == null) data          = new ArrayList<>();

        int   cols       = data.isEmpty() ? 1 : data.get(0).length;
        float margin     = 50f;
        float pageHeight = PDRectangle.A4.getHeight();
        float pageWidth  = PDRectangle.A4.getWidth();
        float rowHeight  = 18f;
        float footerH    = 28f;
        float bottomY    = margin + footerH + rowHeight;
        float colWidth   = (pageWidth - 2 * margin) / Math.max(1, cols);
        PDType1Font font     = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        float leading = 14f;

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = pageHeight - margin;

            cs.beginText(); cs.setFont(fontBold, 16); cs.newLineAtOffset(margin, y);
            cs.showText(trimForPdf(companyName.isEmpty() ? "Company" : companyName, 60));
            cs.endText(); y -= leading;
            if (!companyAddress.isEmpty()) {
                cs.beginText(); cs.setFont(font, 10); cs.newLineAtOffset(margin, y);
                cs.showText(trimForPdf(companyAddress, 70));
                cs.endText(); y -= leading;
            }
            cs.beginText(); cs.setFont(font, 9);
            cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
            cs.newLineAtOffset(margin, y);
            cs.showText("Generated: " + generatedDate);
            cs.endText(); cs.setNonStrokingColor(0f, 0f, 0f); y -= leading * 2;

            cs.beginText(); cs.setFont(fontBold, 14); cs.newLineAtOffset(margin, y);
            cs.showText(trimForPdf(reportTitle, 80));
            cs.endText(); y -= leading * 1.5f;

            if (!summaryLines.isEmpty()) {
                cs.beginText(); cs.setFont(fontBold, 10); cs.newLineAtOffset(margin, y);
                cs.showText("Executive Summary");
                cs.endText(); y -= leading;
                for (String line : summaryLines) {
                    if (y < bottomY) break;
                    cs.beginText(); cs.setFont(font, 9); cs.newLineAtOffset(margin, y);
                    cs.showText(trimForPdf(line, 90));
                    cs.endText(); y -= leading * 0.9f;
                }
                y -= leading * 0.5f;
            }

            int currentPage = 1;
            String[] headerRow = data.isEmpty() ? new String[0] : data.get(0);

            for (int r = 0; r < data.size(); r++) {
                if (y < bottomY) {
                    drawFooter(cs, pageWidth, pageHeight, margin, currentPage, vatRateText, font);
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    currentPage++;
                    cs = new PDPageContentStream(doc, page);
                    y  = pageHeight - margin;
                    if (headerRow.length > 0 && r > 0) {
                        float rowY = y - rowHeight;
                        cs.setNonStrokingColor(0.9f, 0.9f, 0.9f);
                        cs.addRect(margin, rowY, pageWidth - 2 * margin, rowHeight);
                        cs.fill(); cs.setNonStrokingColor(0f, 0f, 0f);
                        drawPdfRowCells(cs, fontBold, font, headerRow, margin, y, rowHeight, colWidth, true);
                        y -= rowHeight;
                    }
                }
                String[] rowData  = data.get(r);
                boolean  isHeader = (r == 0);
                float    rowY     = y - rowHeight;

                if (isHeader) {
                    cs.setNonStrokingColor(0.9f, 0.9f, 0.9f);
                    cs.addRect(margin, rowY, pageWidth - 2 * margin, rowHeight);
                    cs.fill(); cs.setNonStrokingColor(0f, 0f, 0f);
                } else if ((r - 1) % 2 == 1) {
                    cs.setNonStrokingColor(0.97f, 0.97f, 0.97f);
                    cs.addRect(margin, rowY, pageWidth - 2 * margin, rowHeight);
                    cs.fill(); cs.setNonStrokingColor(0f, 0f, 0f);
                }
                drawPdfRowCells(cs, fontBold, font, rowData, margin, y, rowHeight, colWidth, isHeader);
                y -= rowHeight;
            }
            drawFooter(cs, pageWidth, pageHeight, margin, currentPage, vatRateText, font);
            cs.close();
            doc.save(file);
        }
    }

    private static void drawPdfRowCells(PDPageContentStream cs,
            PDType1Font fontBold, PDType1Font font,
            String[] rowData, float margin, float y, float rowHeight,
            float colWidth, boolean isHeader) throws Exception {
        if (rowData == null) return;
        PDType1Font f  = isHeader ? fontBold : font;
        int         fs = isHeader ? 10 : 9;
        float       ty = y - 4;
        for (int c = 0; c < rowData.length; c++) {
            String cell = c < rowData.length ? trimForPdf(rowData[c], 28) : "";
            cs.beginText(); cs.setFont(f, fs);
            cs.newLineAtOffset(margin + 2 + c * colWidth, ty);
            cs.showText(cell); cs.endText();
        }
    }

    private static void drawFooter(PDPageContentStream cs,
            float pageWidth, float pageHeight, float margin,
            int pageNum, String vatRateText, PDType1Font font) throws Exception {
        float y = margin + 12;
        cs.beginText(); cs.setFont(font, 8);
        cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
        cs.newLineAtOffset(margin, y);
        cs.showText(vatRateText); cs.endText();
        cs.beginText(); cs.newLineAtOffset(pageWidth - margin - 36, y);
        cs.showText("Page " + pageNum); cs.endText();
        cs.setNonStrokingColor(0f, 0f, 0f);
    }
}