package org.openarchive.telegram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent adversarial regression suite: no private Telegram data. */
public class IndependentOfficeAccuracyTest {
    private static byte[] packageSheet(String xml)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    private static String display(String xml)throws Exception {
        return OfficePreviewText.render(new ByteArrayInputStream(packageSheet(xml)),"rows.xlsx");
    }
    @Test public void missingRowsMustNotPretendToBeAdjacent()throws Exception {
        String actual=display("<worksheet><sheetData>"+
            "<row r='1'><c r='A1'><v>Верх</v></c></row>"+
            "<row r='100'><c r='A100'><v>Низ</v></c></row>"+
            "</sheetData></worksheet>");
        assertTrue(actual,actual.contains("Строка 1: "));
        assertTrue(actual,actual.contains("пропущено строк: 98"));
        assertTrue(actual,actual.contains("Строка 100: "));
    }
    @Test public void invalidSharedStringIndexMustNeverAppearAsRealCell()throws Exception {
        String input="<worksheet><sheetData><row r='1'><c r='A1' t='s'><v>123</v></c></row></sheetData></worksheet>";
        try{display(input);fail("A non-existent shared string must not display '123' as text");}
        catch(org.xml.sax.SAXException expected){assertTrue(expected.getMessage(),expected.getMessage().contains("общий текст"));}
    }
    @Test public void duplicateAndDescendingRowsAreRejected()throws Exception {
        for(String numbers:new String[]{"1,1","9,2","1048577,1048578"}){
            String[] n=numbers.split(",");
            String xml="<worksheet><sheetData><row r='"+n[0]+"'><c><v>A</v></c></row>"+
                "<row r='"+n[1]+"'><c><v>B</v></c></row></sheetData></worksheet>";
            try{display(xml);fail("Invalid rows should be refused: "+numbers);}
            catch(org.xml.sax.SAXException expected){assertTrue(expected.getMessage(),expected.getMessage().contains("строки"));}
        }
    }
    @Test public void normalAdjacentRowsStillRender()throws Exception {
        String result=display("<worksheet><sheetData><row r='1'><c r='A1'><v>10</v></c></row>"+
            "<row r='2'><c r='A2'><v>20</v></c></row></sheetData></worksheet>");
        assertTrue(result,result.contains("Строка 1: 10"));
        assertTrue(result,result.contains("Строка 2: 20"));
    }
}
