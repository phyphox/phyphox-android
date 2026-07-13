package de.rwth_aachen.phyphox.helper;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

public class XlsxWriterTest {

    //Writes a small workbook and returns all zip entries as parsed XML documents.
    // Parsing implicitly checks that every part is well-formed XML.
    private Map<String, Document> writeAndParse(XlsxWriterTestContent content) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        XlsxWriter xlsx = new XlsxWriter(os);
        content.write(xlsx);
        xlsx.close();

        Map<String, Document> parts = new HashMap<>();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(os.toByteArray()));
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            ByteArrayOutputStream part = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = zip.read(buffer)) != -1)
                part.write(buffer, 0, n);
            parts.put(entry.getName(), DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(part.toByteArray())));
        }
        return parts;
    }

    private interface XlsxWriterTestContent {
        void write(XlsxWriter xlsx) throws Exception;
    }

    @Test
    public void writesAllRequiredParts() throws Exception {
        Map<String, Document> parts = writeAndParse(xlsx -> {
            xlsx.startSheet("Data");
            xlsx.startRow();
            xlsx.stringCell("x", true);
            xlsx.endRow();
            xlsx.startRow();
            xlsx.numberCell(1.5);
            xlsx.endRow();
        });

        assertThat(parts.keySet()).containsExactly(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml");
    }

    @Test
    public void writesCellValuesAndTypes() throws Exception {
        Map<String, Document> parts = writeAndParse(xlsx -> {
            xlsx.startSheet("Data");
            xlsx.startRow();
            xlsx.stringCell("acceleration <&> \"x\"", true);
            xlsx.endRow();
            xlsx.startRow();
            xlsx.numberCell(-1.25e-5);
            xlsx.endRow();
            xlsx.startRow();
            xlsx.numberCell(Double.NaN);
            xlsx.endRow();
        });

        Document sheet = parts.get("xl/worksheets/sheet1.xml");
        NodeList rows = sheet.getElementsByTagName("row");
        assertThat(rows.getLength()).isEqualTo(3);

        Element header = (Element) ((Element) rows.item(0)).getElementsByTagName("c").item(0);
        assertThat(header.getAttribute("t")).isEqualTo("inlineStr");
        assertThat(header.getAttribute("s")).isEqualTo("1"); //bold style
        assertThat(header.getTextContent()).isEqualTo("acceleration <&> \"x\"");

        Element number = (Element) ((Element) rows.item(1)).getElementsByTagName("c").item(0);
        assertThat(number.getAttribute("t")).isEmpty(); //default type: number
        assertThat(Double.parseDouble(number.getTextContent())).isEqualTo(-1.25e-5);

        Element nan = (Element) ((Element) rows.item(2)).getElementsByTagName("c").item(0);
        assertThat(nan.getAttribute("t")).isEqualTo("inlineStr"); //NaN must not be written as a number
        assertThat(nan.getTextContent()).isEqualTo("NaN");
    }

    @Test
    public void registersEachSheetInWorkbookAndRelationships() throws Exception {
        Map<String, Document> parts = writeAndParse(xlsx -> {
            xlsx.startSheet("First");
            xlsx.startSheet("Second");
        });

        NodeList sheets = parts.get("xl/workbook.xml").getElementsByTagName("sheet");
        assertThat(sheets.getLength()).isEqualTo(2);
        assertThat(((Element) sheets.item(0)).getAttribute("name")).isEqualTo("First");
        assertThat(((Element) sheets.item(1)).getAttribute("name")).isEqualTo("Second");
        assertThat(((Element) sheets.item(1)).getAttribute("sheetId")).isEqualTo("2");

        assertThat(parts.keySet()).containsAtLeast("xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml");

        //Each sheet and the styles need a relationship entry
        NodeList rels = parts.get("xl/_rels/workbook.xml.rels").getElementsByTagName("Relationship");
        assertThat(rels.getLength()).isEqualTo(3);
    }

    @Test
    public void sanitizesSheetNames() throws Exception {
        Map<String, Document> parts = writeAndParse(xlsx -> {
            xlsx.startSheet("Data [1]: x/y \\ z*?");           //forbidden characters
            xlsx.startSheet("This sheet name is far too long to be allowed in Excel"); //too long
            xlsx.startSheet("Duplicate");
            xlsx.startSheet("Duplicate");                      //must be made unique
            xlsx.startSheet("");                               //must not be empty
        });

        NodeList sheets = parts.get("xl/workbook.xml").getElementsByTagName("sheet");
        String[] names = new String[sheets.getLength()];
        for (int i = 0; i < sheets.getLength(); i++) {
            names[i] = ((Element) sheets.item(i)).getAttribute("name");
            assertThat(names[i].length()).isAtMost(31);
            assertThat(names[i]).doesNotContainMatch("[\\[\\]:*?/\\\\]");
            assertThat(names[i]).isNotEmpty();
        }
        assertThat(names[2]).isNotEqualTo(names[3]);
    }
}
