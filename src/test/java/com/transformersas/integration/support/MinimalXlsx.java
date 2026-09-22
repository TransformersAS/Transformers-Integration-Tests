package com.transformersas.integration.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Construye un .xlsx mínimo (una sola hoja, celdas de texto) sin depender de ninguna librería de Excel: alcanza para
 * subir los archivos que el backend valida al leer una carga masiva (CU-15), sin duplicar su lógica de lectura ni
 * agregar una dependencia solo para generar el archivo de prueba.
 */
public final class MinimalXlsx {

    private MinimalXlsx() {
    }

    /** La primera fila son los encabezados; cada fila de datos es una lista de textos en el mismo orden. */
    public static byte[] build(List<String> headers, List<List<String>> rows) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                write(zip, "[Content_Types].xml", CONTENT_TYPES);
                write(zip, "_rels/.rels", RELS);
                write(zip, "xl/workbook.xml", WORKBOOK);
                write(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
                write(zip, "xl/worksheets/sheet1.xml", sheet(headers, rows));
            }
            return buffer.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String sheet(List<String> headers, List<List<String>> rows) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        row(xml, 1, headers);
        for (int index = 0; index < rows.size(); index++) {
            row(xml, index + 2, rows.get(index));
        }
        return xml.append("</sheetData></worksheet>").toString();
    }

    private static void row(StringBuilder xml, int rowNumber, List<String> values) {
        xml.append("<row r=\"").append(rowNumber).append("\">");
        for (int column = 0; column < values.size(); column++) {
            String value = values.get(column);
            if (value != null && !value.isEmpty()) {
                xml.append("<c r=\"").append(columnLetter(column)).append(rowNumber)
                        .append("\" t=\"inlineStr\"><is><t>").append(escape(value)).append("</t></is></c>");
            }
        }
        xml.append("</row>");
    }

    private static String columnLetter(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" "
            + "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" "
            + "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "</Types>";

    private static final String RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" "
            + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
            + "Target=\"xl/workbook.xml\"/></Relationships>";

    private static final String WORKBOOK = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"Datos\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";

    private static final String WORKBOOK_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" "
            + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" "
            + "Target=\"worksheets/sheet1.xml\"/></Relationships>";
}
