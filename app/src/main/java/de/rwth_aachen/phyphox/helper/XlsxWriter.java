package de.rwth_aachen.phyphox.helper;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

//Minimal writer for the xlsx format (Office Open XML spreadsheet, ECMA-376), which allows us to
// export to Excel without an external library.
//An xlsx file is a zip archive containing a few XML files: a content type declaration
// ([Content_Types].xml), relationship files pointing to the actual content (*.rels), a workbook
// definition listing the sheets (xl/workbook.xml), a style definition (xl/styles.xml, here only
// used to provide a bold font for header cells) and one XML file per worksheet
// (xl/worksheets/sheetN.xml).
//Cell data is streamed directly to the output stream, so large datasets do not have to be held
// in memory. Only the sheet names are collected until the workbook metadata is written on
// close().
//Intentional limitations to keep this minimal: strings are stored inline instead of using a
// shared string table, the optional cell and row references (r attributes) are omitted (cells
// simply fill each row from left to right), and there are no number formats or styles beyond
// the bold header font.
public class XlsxWriter implements Closeable {

    private final ZipOutputStream zip;
    private final Writer writer;
    private final List<String> sheetNames = new ArrayList<>();
    private final Set<String> usedSheetNames = new HashSet<>(); //lower case, Excel treats sheet names as case-insensitive
    private boolean sheetOpen = false;

    public XlsxWriter(OutputStream os) throws IOException {
        zip = new ZipOutputStream(os);
        writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);

        //The package relationship file is static and can be written right away. Everything else
        // depends on the number of sheets and is written in close().
        zip.putNextEntry(new ZipEntry("_rels/.rels"));
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>");
        writer.flush();
        zip.closeEntry();
    }

    public void startSheet(String name) throws IOException {
        if (sheetOpen)
            endSheet();
        sheetNames.add(uniqueSheetName(name));
        zip.putNextEntry(new ZipEntry("xl/worksheets/sheet" + sheetNames.size() + ".xml"));
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<sheetData>");
        sheetOpen = true;
    }

    public void endSheet() throws IOException {
        writer.write("</sheetData></worksheet>");
        writer.flush();
        zip.closeEntry();
        sheetOpen = false;
    }

    public void startRow() throws IOException {
        writer.write("<row>");
    }

    public void endRow() throws IOException {
        writer.write("</row>");
    }

    public void stringCell(String value, boolean bold) throws IOException {
        writer.write("<c t=\"inlineStr\"" + (bold ? " s=\"1\"" : "") + "><is><t xml:space=\"preserve\">" + escape(value) + "</t></is></c>");
    }

    public void numberCell(double value) throws IOException {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            //NaN and infinity are not valid numbers in xlsx, store them as text
            stringCell(Double.toString(value), false);
            return;
        }
        writer.write("<c><v>" + value + "</v></c>");
    }

    //Writes the workbook metadata derived from the collected sheet names and completes the file
    @Override
    public void close() throws IOException {
        if (sheetOpen)
            endSheet();

        StringBuilder sb = new StringBuilder();

        zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (int i = 1; i <= sheetNames.size(); i++)
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        sb.append("</Types>");
        writer.write(sb.toString());
        writer.flush();
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
        sb.setLength(0);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        sb.append("<sheets>");
        for (int i = 1; i <= sheetNames.size(); i++)
            sb.append("<sheet name=\"").append(escape(sheetNames.get(i-1))).append("\" sheetId=\"").append(i).append("\" r:id=\"rId").append(i).append("\"/>");
        sb.append("</sheets>");
        sb.append("</workbook>");
        writer.write(sb.toString());
        writer.flush();
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
        sb.setLength(0);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheetNames.size(); i++)
            sb.append("<Relationship Id=\"rId").append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        sb.append("<Relationship Id=\"rId").append(sheetNames.size() + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        sb.append("</Relationships>");
        writer.write(sb.toString());
        writer.flush();
        zip.closeEntry();

        //Font 0 is the default font, font 1 is bold. Cell style (cellXfs) 0 is the default, 1
        // uses the bold font and is referenced by bold cells as s="1". The empty fills, border
        // and cellStyleXfs entries are the minimum Excel expects to find in a style sheet.
        zip.putNextEntry(new ZipEntry("xl/styles.xml"));
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"2\">" +
                "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
                "</fonts>" +
                "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"2\">" +
                "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
                "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
                "</cellXfs>" +
                "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
                "</styleSheet>");
        writer.flush();
        zip.closeEntry();

        zip.close();
    }

    //Escape reserved XML characters and remove control characters that may not occur in XML 1.0
    private static String escape(String s) {
        if (s == null)
            return "";
        return s.replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]", "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    //Excel imposes restrictions on sheet names: Certain characters are forbidden, at most 31
    // characters, not empty, no duplicates (case-insensitive) and no apostrophe at either end
    private String uniqueSheetName(String name) {
        String s = (name == null) ? "" : name.replaceAll("[\\[\\]:*?/\\\\\\x00-\\x1f]", " ").trim();
        while (s.startsWith("'"))
            s = s.substring(1);
        while (s.endsWith("'"))
            s = s.substring(0, s.length() - 1);
        s = s.trim();
        if (s.isEmpty())
            s = "Sheet";
        if (s.length() > 31)
            s = s.substring(0, 31).trim();
        String base = s;
        int i = 2;
        while (!usedSheetNames.add(s.toLowerCase(Locale.ROOT))) {
            String suffix = " (" + i + ")";
            s = base.substring(0, Math.min(base.length(), 31 - suffix.length())) + suffix;
            i++;
        }
        return s;
    }
}
