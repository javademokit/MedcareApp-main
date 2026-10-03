package com.example.MedcareApp.services;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PayslipPdfGenerator {
    private PayslipPdfGenerator() {}

    public static byte[] generate(List<String> rows) {
        List<List<String>> pages = new ArrayList<>();
        for (int start = 0; start < rows.size(); start += 50) {
            pages.add(rows.subList(start, Math.min(start + 50, rows.size())));
        }
        if (pages.isEmpty()) pages.add(List.of());
        int fontObjectId = 3 + (2 * pages.size());
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        String kids = java.util.stream.IntStream.range(0, pages.size())
                .mapToObj(index -> (3 + (2 * index)) + " 0 R")
                .collect(java.util.stream.Collectors.joining(" "));
        objects.add("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>");
        for (int index = 0; index < pages.size(); index++) {
            int pageObjectId = 3 + (2 * index);
            int contentObjectId = pageObjectId + 1;
            StringBuilder stream = new StringBuilder("BT\n/F1 10 Tf\n48 790 Td\n14 TL\n");
            for (String row : pages.get(index)) {
                stream.append('(').append(escape(row)).append(") Tj\nT*\n");
            }
            stream.append("ET");
            String streamBody = stream.toString();
            int contentLength = streamBody.getBytes(StandardCharsets.US_ASCII).length;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /Font << /F1 " + fontObjectId + " 0 R >> >> "
                    + "/Contents " + contentObjectId + " 0 R >>");
            objects.add("<< /Length " + contentLength + " >>\nstream\n" + streamBody + "\nendstream");
        }
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.size() + 1];
        for (int index = 0; index < objects.size(); index++) {
            offsets[index + 1] = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
            pdf.append(index + 1).append(" 0 obj\n").append(objects.get(index)).append("\nendobj\n");
        }
        int xrefOffset = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n")
                .append("0000000000 65535 f \n");
        for (int index = 1; index < offsets.length; index++) {
            pdf.append(String.format("%010d 00000 n \n", offsets[index]));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1)
                .append(" /Root 1 0 R >>\nstartxref\n").append(xrefOffset).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String escape(String value) {
        String ascii = value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
        return ascii.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
