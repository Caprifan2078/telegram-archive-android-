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
    @Test public void spreadsheetPreservesEmptyColumnsAndRejectsMisorderedCells()throws Exception{
        String xml="<worksheet><sheetData><row><c r='A1'><v>10</v></c><c r='C1'><v>20</v></c></row></sheetData></worksheet>";
        String rendered=OfficePreviewText.render(new ByteArrayInputStream(doc("xl/worksheets/sheet1.xml",xml)),"x.xlsx");
        assertTrue("Missing B must remain an empty column",rendered.contains("A1: 10\t\tC1: 20"));
        String bad="<worksheet><sheetData><row><c r='C1'><v>20</v></c><c r='A1'><v>10</v></c></row></sheetData></worksheet>";
        try{OfficePreviewText.render(new ByteArrayInputStream(doc("xl/worksheets/sheet1.xml",bad)),"x.xlsx");fail("Out-of-order cells must not be shifted");}
        catch(org.xml.sax.SAXException expected){}
    }
    @Test public void oversizedTextDoesNotSilentlyTruncate()throws Exception{
        StringBuilder text=new StringBuilder();for(int i=0;i<140000;i++)text.append('a');
        String xml="<w:document xmlns:w='urn:x'><w:body><w:p><w:r><w:t>"+text+"</w:t><w:t>UNIQUE_TRAILER</w:t></w:r></w:p></w:body></w:document>";
        try{OfficePreviewText.render(new ByteArrayInputStream(doc("word/document.xml",xml)),"x.docx");fail("Overlong preview must fail clearly");}
        catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("много текста"));}
    }
    @Test public void ignoredZipMemberCannotExpandWithoutLimit()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document xmlns:w='urn:x'><w:p><w:t>safe</w:t></w:p></w:document>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/media/huge.bin"));
            byte[] chunk=new byte[8192];for(int i=0;i<8193;i++)zip.write(chunk);zip.closeEntry();
        }
        try{OfficePreviewText.render(new ByteArrayInputStream(out.toByteArray()),"x.docx");fail("Inflated ignored content must be bounded");}
        catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("слишком много данных"));}
    }
    @Test public void sharedStringsAreResolvedAndInvalidIndexIsNotPresentedAsData()throws Exception{
        String sheet="<worksheet><sheetData><row><c r='A1' t='s'><v>0</v></c></row></sheetData></worksheet>";
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write("<sst><si><t>Общий текст</t></si></sst>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));zip.write(sheet.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        assertTrue(OfficePreviewText.render(new ByteArrayInputStream(out.toByteArray()),"x.xlsx").contains("Общий текст"));
        String bad="<worksheet><sheetData><row><c r='A1' t='s'><v>99</v></c></row></sheetData></worksheet>";
        try{OfficePreviewText.render(new ByteArrayInputStream(doc("xl/worksheets/sheet1.xml",bad)),"x.xlsx");fail("Invalid shared string must not render as a number");}
        catch(org.xml.sax.SAXException expected){}
    }
    @Test public void duplicateOfficePartNamesAreRejected()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            for(int i=1;i<=2;i++){
                zip.putNextEntry(new ZipEntry("xl/worksheets/sheet"+i+".xml"));
                zip.write("<worksheet><sheetData/></worksheet>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
        }
        byte[] bytes=out.toByteArray();
        byte[] from="xl/worksheets/sheet2.xml".getBytes(StandardCharsets.UTF_8);
        byte[] to="xl/worksheets/sheet1.xml".getBytes(StandardCharsets.UTF_8);int replacements=0;
        for(int i=0;i<=bytes.length-from.length;i++){
            boolean match=true;for(int j=0;j<from.length;j++)if(bytes[i+j]!=from[j]){match=false;break;}
            if(match){System.arraycopy(to,0,bytes,i,to.length);replacements++;i+=from.length-1;}
        }
        assertEquals("Both local and central directory names must be changed",2,replacements);
        try{OfficePreviewText.render(new ByteArrayInputStream(bytes),"x.xlsx");fail("Duplicate sheet part must not appear as two sheets");}
        catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("повторяется часть"));}
    }
}
