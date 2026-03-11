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
        int cols = data.isEmpty() ? 1 : data.get(0).length;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float margin = 50;
            float pageHeight = page.getMediaBox().getHeight();
            float pageWidth = page.getMediaBox().getWidth();
            float y = pageHeight - margin;
            float leading = 14f;
            float colWidth = (pageWidth - 2 * margin) / Math.max(1, cols);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            cs.beginText();
            cs.setFont(fontBold, 14);
            cs.newLineAtOffset(margin, y);
            cs.showText(trimForPdf(title != null ? title : "Report", 80));
            cs.endText();
            y -= leading * 2;

            float bottomY = margin + leading;
            for (int r = 0; r < data.size(); r++) {
                if (y < bottomY) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    y = pageHeight - margin;
                    cs = new PDPageContentStream(doc, page);
                }
                String[] rowData = data.get(r);
                cs.beginText();
                cs.setFont(r == 0 ? fontBold : font, 10);
                cs.newLineAtOffset(margin, y);
                for (String cell : rowData) {
                    cs.showText(trimForPdf(cell, 28));
                    cs.newLineAtOffset(colWidth, 0);
                }
                cs.endText();
                y -= leading;
            }
            cs.close();
            doc.save(file);
        }
    }

    /**
     * Exports the given TableView to a PDF file with the given title.
     */
    public static void exportToPDF(TableView<?> table, String title, File file) throws Exception {
        List<String> headers = new ArrayList<>();
        for (TableColumn<?, ?> col : table.getColumns()) {
            String t = col.getText() != null ? col.getText() : col.getId();
            headers.add(t != null ? t : "");
        }
        List<List<String>> rows = new ArrayList<>();
        for (Object row : table.getItems()) {
            List<String> cells = new ArrayList<>();
            for (TableColumn<?, ?> col : table.getColumns()) {
                @SuppressWarnings("unchecked")
                TableColumn<Object, ?> c = (TableColumn<Object, ?>) col;
                Object val = c.getCellData(row);
                cells.add(val != null ? val.toString() : "");
            }
            rows.add(cells);
        }

        int cols = Math.max(1, headers.size());
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float margin = 50;
            float pageHeight = page.getMediaBox().getHeight();
            float pageWidth = page.getMediaBox().getWidth();
            float y = pageHeight - margin;
            float leading = 12f;
            float colWidth = (pageWidth - 2 * margin) / cols;
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            cs.beginText();
            cs.setFont(fontBold, 14);
            cs.newLineAtOffset(margin, y);
            cs.showText(trimForPdf(title != null ? title : "Report", 80));
            cs.endText();
            y -= leading * 2;

            List<List<String>> allRows = new ArrayList<>();
            allRows.add(headers);
            allRows.addAll(rows);
            float bottomY = margin + leading;

            for (int r = 0; r < allRows.size(); r++) {
                if (y < bottomY) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    y = pageHeight - margin;
                    cs = new PDPageContentStream(doc, page);
                }
                List<String> rowData = allRows.get(r);
                cs.beginText();
                cs.setFont(r == 0 ? fontBold : font, 9);
                cs.newLineAtOffset(margin, y);
                for (String cell : rowData) {
                    cs.showText(trimForPdf(cell, 22));
                    cs.newLineAtOffset(colWidth, 0);
                }
                cs.endText();
                y -= leading;
            }
            cs.close();
            doc.save(file);
        }
    }
}
