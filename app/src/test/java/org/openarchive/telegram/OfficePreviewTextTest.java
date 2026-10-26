package org.openarchive.telegram;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class OfficePreviewTextTest {
    private byte[] doc(String name,String payload)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            zip.putNextEntry(new ZipEntry(name));
            zip.write(payload.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        return out.toByteArray();
    }
    @Test public void wordParagraphsBecomeReadableText()throws Exception{
        String xml="<w:document xmlns:w='urn:x'><w:body><w:p><w:r><w:t>Привет мир</w:t></w:r></w:p></w:body></w:document>";
        String rendered=OfficePreviewText.render(new ByteArrayInputStream(doc("word/document.xml",xml)),"message.docx");
        assertTrue(rendered.contains("Привет мир"));
    }
    @Test public void excelCellValuesAreReadable()throws Exception{
        String xml="<worksheet><sheetData><row><c><v>17</v></c><c><v>25</v></c></row></sheetData></worksheet>";
        String rendered=OfficePreviewText.render(new ByteArrayInputStream(doc("xl/worksheets/sheet1.xml",xml)),"budget.xlsx");
        assertTrue(rendered.contains("17\t25"));
    }
    @Test public void slidesHaveSlideHeadings()throws Exception{
        String xml="<p:sld xmlns:p='urn:p' xmlns:a='urn:a'><a:p><a:r><a:t>Наш проект</a:t></a:r></a:p></p:sld>";
        String rendered=OfficePreviewText.render(new ByteArrayInputStream(doc("ppt/slides/slide1.xml",xml)),"intro.pptx");
        assertTrue(rendered.contains("СЛАЙД 1"));assertTrue(rendered.contains("Наш проект"));
    }
    @Test public void xmlExternalEntitiesFailClosed()throws Exception{
        String xml="<!DOCTYPE x [<!ENTITY xx SYSTEM 'file:///etc/passwd'>]><w:document><w:p><w:t>&xx;</w:t></w:p></w:document>";
        try{OfficePreviewText.render(new ByteArrayInputStream(doc("word/document.xml",xml)),"x.docx");fail("DOCTYPE must be refused");}
        catch(Exception expected){}
    }
    @Test public void binaryDocumentsAreNotPretendedToRender()throws Exception{
        try{OfficePreviewText.render(new ByteArrayInputStream(new byte[]{1,2,3}),"old.doc");fail();}
        catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("бинарный"));}
    }
}
