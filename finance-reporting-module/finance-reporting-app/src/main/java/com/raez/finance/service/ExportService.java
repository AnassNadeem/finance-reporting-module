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
 * Exports TableView data to CSV and PDF.
 */
public class ExportService {

    /**
     * Exports the given TableView to a CSV file. Uses column headers and cell values via getCellData.
     */
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

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String trimForPdf(String s, int maxLen) {
        if (s == null) return "";
        s = s.replaceAll("[\\x00-\\x1f]", " ");
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    /**
     * Exports raw rows (header + data) to a CSV file.
     * Each element of {@code data} is a String array representing one row.
     */
    public static void exportRowsToCSV(List<String[]> data, File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String[] row : data) {
            List<String> escaped = new ArrayList<>();
            for (String cell : row) {
                escaped.add(escapeCsv(cell != null ? cell : ""));
            }
            sb.append(String.join(",", escaped)).append("\n");
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
    }

    /**
     * Exports raw rows (header + data) to a PDF file with the given title.
     * Each element of {@code data} is a String array representing one row.
     */
    public static void exportRowsToPDF(String title, List<String[]> data, File file) throws Exception {
        exportRowsToPDFProfessional(title, null, data, file);
    }

    /**
     * Professional multi-page PDF: letterhead (company name/address from Global Settings),
     * report title and generation date, executive summary (3–4 metrics), paginated data table
     * with alternating row colours, and footer with page number and VAT rate.
     */
    public static void exportRowsToPDFProfessional(String reportTitle, List<String> summaryLines,
                                                    List<String[]> data, File file) throws Exception {
        GlobalSettingsService gs = GlobalSettingsService.getInstance();
        String companyName = gs.getCompanyName() != null ? gs.getCompanyName() : "";
        String companyAddress = gs.getCompanyAddress() != null ? gs.getCompanyAddress() : "";
        String vatRateText = String.format("VAT rate applied: %s%%", String.valueOf((int) Math.round(gs.getDefaultVatPercent())));
        String generatedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        if (reportTitle == null) reportTitle = "Report";
        if (summaryLines == null) summaryLines = new ArrayList<>();
        if (data == null) data = new ArrayList<>();

        int cols = data.isEmpty() ? 1 : data.get(0).length;
        float margin = 50f;
        float pageHeight = PDRectangle.A4.getHeight();
        float pageWidth = PDRectangle.A4.getWidth();
        float rowHeight = 18f;
        float footerHeight = 28f;
        float bottomY = margin + footerHeight + rowHeight;
        float colWidth = (pageWidth - 2 * margin) / Math.max(1, cols);
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        float leading = 14f;

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = pageHeight - margin;

            // Letterhead
            cs.beginText();
            cs.setFont(fontBold, 16);
            cs.newLineAtOffset(margin, y);
            cs.showText(trimForPdf(companyName.isEmpty() ? "Company" : companyName, 60));
            cs.endText();
            y -= leading;
            if (!companyAddress.isEmpty()) {
                cs.beginText();
                cs.setFont(font, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText(trimForPdf(companyAddress, 70));
                cs.endText();
                y -= leading;
            }
            cs.beginText();
            cs.setFont(font, 9);
            cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
            cs.newLineAtOffset(margin, y);
            cs.showText("Generated: " + generatedDate);
            cs.endText();
            cs.setNonStrokingColor(0f, 0f, 0f);
            y -= leading * 2;

            // Report title
            cs.beginText();
            cs.setFont(fontBold, 14);
            cs.newLineAtOffset(margin, y);
            cs.showText(trimForPdf(reportTitle, 80));
            cs.endText();
            y -= leading * 1.5f;

            // Executive summary
            if (!summaryLines.isEmpty()) {
                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Executive Summary");
                cs.endText();
                y -= leading;
                cs.setFont(font, 9);
                for (String line : summaryLines) {
                    if (y < bottomY) break;
                    cs.beginText();
                    cs.newLineAtOffset(margin, y);
                    cs.showText(trimForPdf(line, 90));
                    cs.endText();
                    y -= leading * 0.9f;
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
                    y = pageHeight - margin;
                    // Repeat header on new page
                    if (headerRow.length > 0 && r > 0) {
                        float rowY = y - rowHeight;
                        cs.setNonStrokingColor(0.9f, 0.9f, 0.9f);
                        cs.addRect(margin, rowY, pageWidth - 2 * margin, rowHeight);
                        cs.fill();
                        cs.setNonStrokingColor(0f, 0f, 0f);
                        cs.beginText();
                        cs.setFont(fontBold, 10);
                        cs.newLineAtOffset(margin + 2, y - 4);
                        for (int c = 0; c < headerRow.length; c++) {
                            cs.showText(trimForPdf(headerRow[c], 24));
                            if (c < headerRow.length - 1) cs.newLineAtOffset(colWidth, 0);
                        }
                        cs.endText();
                        y -= rowHeight;
                    }
                }
                String[] rowData = data.get(r);
                boolean isHeader = (r == 0);
                float rowY = y - rowHeight;

                if (isHeader) {
                    cs.setNonStrokingColor(0.9f, 0.9f, 0.9f);
                    cs.addRect(margin, rowY, pageWidth - 2 * margin, rowHeight);
                    cs.fill();
                    cs.setNonStrokingColor(0f, 0f, 0f);
                } else if ((r - 1) % 2 == 1) {
                    cs.setNonStrokingColor(0.97f, 0.97f, 0.97f);
                    cs.addRect(margin, rowY, pageWidth - 2 * margin, rowHeight);
                    cs.fill();
                    cs.setNonStrokingColor(0f, 0f, 0f);
                }

                cs.beginText();
                cs.setFont(isHeader ? fontBold : font, isHeader ? 10 : 9);
                cs.newLineAtOffset(margin + 2, y - 4);
                for (int c = 0; c < rowData.length; c++) {
                    String cell = c < rowData.length ? trimForPdf(rowData[c], 24) : "";
                    cs.showText(cell);
                    if (c < rowData.length - 1) cs.newLineAtOffset(colWidth, 0);
                }
                cs.endText();
                y -= rowHeight;
            }

            drawFooter(cs, pageWidth, pageHeight, margin, currentPage, vatRateText, font);
            cs.close();
            doc.save(file);
        }
    }

    private static void drawFooter(PDPageContentStream cs, float pageWidth, float pageHeight,
                                  float margin, int pageNum, String vatRateText, PDType1Font font) throws Exception {
        float y = margin + 12;
        cs.beginText();
        cs.setFont(font, 8);
        cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
        cs.newLineAtOffset(margin, y);
        cs.showText(vatRateText);
        cs.endText();
        String pageStr = "Page " + pageNum;
        cs.beginText();
        cs.newLineAtOffset(pageWidth - margin - 36, y);
        cs.showText(pageStr);
        cs.endText();
        cs.setNonStrokingColor(0f, 0f, 0f);
    }

    /**
     * Exports the given TableView to a PDF file with the given title.
     * Uses professional layout: letterhead, executive summary (row count), paginated table, footer with VAT rate.
     */
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
        summary.add("Report: " + (title != null ? title : "Report"));
        summary.add("Total rows: " + (data.size() - 1));
        exportRowsToPDFProfessional(title != null ? title : "Report", summary, data, file);
    }
}
