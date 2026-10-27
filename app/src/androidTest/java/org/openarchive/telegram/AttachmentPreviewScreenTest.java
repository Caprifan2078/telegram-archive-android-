package org.openarchive.telegram;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AttachmentPreviewScreenTest {
    @Rule public ActivityTestRule<AttachmentPreviewActivity> screen=
        new ActivityTestRule<>(AttachmentPreviewActivity.class,true,false);
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context app=instrumentation.getTargetContext();

    private File generate(String ext,String entry,String text)throws Exception {
        File f=File.createTempFile("preview-ui-",ext,app.getCacheDir());
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(f))){
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(text.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return f;
    }
    private boolean contains(View v,String expected){
        if(v instanceof TextView && ((TextView)v).getText().toString().contains(expected))return true;
        if(v instanceof ViewGroup){
            ViewGroup parent=(ViewGroup)v;
            for(int i=0;i<parent.getChildCount();i++)
                if(contains(parent.getChildAt(i),expected))return true;
        }
        return false;
    }
    private String visibleText(View view){
        StringBuilder result=new StringBuilder();
        if(view instanceof TextView)result.append(((TextView)view).getText()).append(" | ");
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)result.append(visibleText(group.getChildAt(i)));
        }
        return result.toString();
    }
    private android.widget.Button findButton(View view,String label){
        if(view instanceof android.widget.Button&&((android.widget.Button)view).getText().toString().contains(label))
            return (android.widget.Button)view;
        if(view instanceof ViewGroup){
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                android.widget.Button found=findButton(group.getChildAt(i),label);if(found!=null)return found;
            }
        }
        return null;
    }
    private boolean clickText(View view,String label){
        if(view instanceof android.widget.Button &&
            ((android.widget.Button)view).getText().toString().contains(label)){
            view.performClick();return true;
        }
        if(view instanceof ViewGroup){
            ViewGroup parent=(ViewGroup)view;
            for(int i=0;i<parent.getChildCount();i++)
                if(clickText(parent.getChildAt(i),label))return true;
        }
        return false;
    }
    private void awaitText(String expected)throws Exception{
        long deadline=android.os.SystemClock.uptimeMillis()+9000;
        while(android.os.SystemClock.uptimeMillis()<deadline){
            instrumentation.waitForIdleSync();
            if(contains(screen.getActivity().findViewById(android.R.id.content),expected))return;
            Thread.sleep(110);
        }
        fail("Preview screen never displayed: "+expected+"; actual UI: "+
            visibleText(screen.getActivity().findViewById(android.R.id.content)));
    }
    private void awaitArchiveScroll(int expected)throws Exception{
        long deadline=android.os.SystemClock.uptimeMillis()+4000;final int[] current={-1};
        while(android.os.SystemClock.uptimeMillis()<deadline){
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(()->{try{
                java.lang.reflect.Field field=AttachmentPreviewActivity.class.getDeclaredField("scroll");field.setAccessible(true);
                current[0]=((android.widget.ScrollView)field.get(screen.getActivity())).getScrollY();
            }catch(Exception e){throw new AssertionError(e);}});
            if(current[0]==expected)return;
            Thread.sleep(80);
        }
        assertEquals("Archive list scroll should be restored",expected,current[0]);
    }
    @Test public void docxAppearsAsReadableOfflineText()throws Exception{
        String document="<w:document xmlns:w='urn:w'><w:body><w:p><w:r><w:t>Документ из переписки</w:t></w:r></w:p></w:body></w:document>";
        File file=generate(".docx","word/document.xml",document);
        try {
            Intent i=new Intent(app,AttachmentPreviewActivity.class);
            i.setData(Uri.fromFile(file));i.putExtra("display_name","Отчёт.docx");
            screen.launchActivity(i);
            awaitText("Документ из переписки");
            awaitText("Только просмотр");
            assertTrue(contains(screen.getActivity().findViewById(android.R.id.content),"Сложная разметка"));
            // Screenshot shows ONLY a generated document, never a real user's chats.
            try(ParcelFileDescriptor p=instrumentation.getUiAutomation().executeShellCommand(
                "screencap -p /sdcard/Download/ta_office_preview_synthetic.png");
                FileInputStream stream=new FileInputStream(p.getFileDescriptor())){
                byte[] buffer=new byte[256];while(stream.read(buffer)!=-1){}
            }
        } finally {file.delete();}
    }
    @Test public void zipListsSafeEntriesAndNeverNeedsExternalApp()throws Exception{
        File file=generate(".zip","documents/hello.txt","Synthetic archive preview");
        try {
            Intent i=new Intent(app,AttachmentPreviewActivity.class);
            i.setData(Uri.fromFile(file));i.putExtra("display_name","Чат.zip");
            screen.launchActivity(i);
            awaitText("documents/hello.txt");
            awaitText("Вложений: 1");
        } finally {file.delete();}
    }
    @Test public void excelDisplaysCellValuesOnActualAndroidScreen()throws Exception{
        String sheet="<worksheet><sheetData><row><c t='inlineStr'><is><t>Товар</t></is></c>"
            +"<c><v>42</v></c></row></sheetData></worksheet>";
        File file=generate(".xlsx","xl/worksheets/sheet1.xml",sheet);
        try{
            Intent i=new Intent(app,AttachmentPreviewActivity.class);
            i.setData(Uri.fromFile(file));i.putExtra("display_name","Таблица.xlsx");
            screen.launchActivity(i);
            awaitText("Товар");awaitText("42");awaitText("ЛИСТ 1");
            awaitText("Сложная разметка");
        }finally{file.delete();}
    }
    @Test public void presentationDisplaysSlideContentOnActualAndroidScreen()throws Exception{
        String slide="<p:sld xmlns:p='urn:p' xmlns:a='urn:a'><a:p><a:r>"
            +"<a:t>Наш план на завтра</a:t></a:r></a:p></p:sld>";
        File file=generate(".pptx","ppt/slides/slide1.xml",slide);
        try{
            Intent i=new Intent(app,AttachmentPreviewActivity.class);
            i.setData(Uri.fromFile(file));i.putExtra("display_name","Презентация.pptx");
            screen.launchActivity(i);
            awaitText("Наш план на завтра");awaitText("СЛАЙД 1");
            awaitText("Сложная разметка");
        }finally{file.delete();}
    }
    @Test public void zipOpensNestedDocumentThenReturnsToIndex()throws Exception{
        File file=generate(".zip","docs/message.txt","Содержимое архива полностью офлайн");
        try{
            Intent i=new Intent(app,AttachmentPreviewActivity.class);
            i.setData(Uri.fromFile(file));i.putExtra("display_name","Документы.zip");
            screen.launchActivity(i);
            awaitText("docs/message.txt");
            instrumentation.runOnMainSync(()->assertTrue(clickText(
                screen.getActivity().findViewById(android.R.id.content),"docs/message.txt")));
            awaitText("Содержимое архива полностью офлайн");
            instrumentation.runOnMainSync(()->assertTrue(clickText(
                screen.getActivity().findViewById(android.R.id.content),"К архиву")));
            awaitText("Вложений: 1");
        }finally{file.delete();}
    }
    @Test public void maliciousOfficeDocShowsErrorButViewerRemainsAvailable()throws Exception{
        String malicious="<!DOCTYPE doc [<!ENTITY ext SYSTEM 'file:///etc/passwd'>]>"
            +"<w:document><w:p><w:t>&ext;</w:t></w:p></w:document>";
        File file=generate(".docx","word/document.xml",malicious);
        try{
            Intent i=new Intent(app,AttachmentPreviewActivity.class);
            i.setData(Uri.fromFile(file));i.putExtra("display_name","Опасный.docx");
            screen.launchActivity(i);
            awaitText("Не удалось открыть");
            awaitText("XML-декларация");
            awaitText("Закрыть просмотр");
        }finally{file.delete();}
    }

    @Test public void nestedDocxHardwareBackRestoresSameArchiveListPosition()throws Exception{
        File archive=File.createTempFile("synthetic-nested-", ".zip",app.getCacheDir());
        ByteArrayOutputStream docBytes=new ByteArrayOutputStream();
        try(ZipOutputStream doc=new ZipOutputStream(docBytes)){
            doc.putNextEntry(new ZipEntry("word/document.xml"));
            doc.write("<w:document xmlns:w='urn:w'><w:body><w:p><w:r><w:t>Учебный документ офлайн</w:t></w:r></w:p></w:body></w:document>".getBytes(StandardCharsets.UTF_8));
            doc.closeEntry();
        }
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(archive))){
            for(int n=0;n<25;n++){
                zip.putNextEntry(new ZipEntry("other/file"+n+".txt"));
                zip.write(("Synthetic "+n).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("Документы/Учёба/Работа.docx"));
            zip.write(docBytes.toByteArray());zip.closeEntry();
        }
        try{
            Intent intent=new Intent(app,AttachmentPreviewActivity.class);
            intent.setData(Uri.fromFile(archive));intent.putExtra("display_name","Учебный архив.zip");
            screen.launchActivity(intent);awaitText("Документы/Учёба/Работа.docx");
            final int[] position={0};
            instrumentation.runOnMainSync(()->{try{
                java.lang.reflect.Field field=AttachmentPreviewActivity.class.getDeclaredField("scroll");field.setAccessible(true);
                android.widget.ScrollView scroller=(android.widget.ScrollView)field.get(screen.getActivity());
                scroller.scrollTo(0,Math.max(0,scroller.getChildAt(0).getHeight()-scroller.getHeight()));position[0]=scroller.getScrollY();
                assertTrue("The synthetic index must scroll",position[0]>0);
                assertTrue(clickText(screen.getActivity().findViewById(android.R.id.content),"Документы/Учёба/Работа.docx"));
            }catch(Exception e){throw new AssertionError(e);}});
            awaitText("Учебный документ офлайн");
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            awaitText("Вложений: 26");awaitArchiveScroll(position[0]);
            instrumentation.runOnMainSync(()->{try{
                assertFalse("System Back must keep archive preview open",screen.getActivity().isFinishing());
                java.lang.reflect.Field field=AttachmentPreviewActivity.class.getDeclaredField("scroll");field.setAccessible(true);
                android.widget.ScrollView scroller=(android.widget.ScrollView)field.get(screen.getActivity());
                assertEquals("Archive list scroll should be restored",position[0],scroller.getScrollY());
            }catch(Exception e){throw new AssertionError(e);}});
        }finally{archive.delete();}
    }
    @Test public void systemBackDuringNestedLoadDoesNotReopenStaleResult()throws Exception{
        File file=generate(".zip","nested/readme.txt","Synthetic stale callback");
        try{
            Intent intent=new Intent(app,AttachmentPreviewActivity.class);
            intent.setData(Uri.fromFile(file));intent.putExtra("display_name","Тест.zip");screen.launchActivity(intent);
            awaitText("nested/readme.txt");
            instrumentation.runOnMainSync(()->{
                assertTrue(clickText(screen.getActivity().findViewById(android.R.id.content),"nested/readme.txt"));
                screen.getActivity().onBackPressed();
            });
            awaitText("Вложений: 1");
            Thread.sleep(500);instrumentation.waitForIdleSync();
            assertTrue(contains(screen.getActivity().findViewById(android.R.id.content),"Вложений: 1"));
            assertFalse(contains(screen.getActivity().findViewById(android.R.id.content),"Synthetic stale callback"));
        }finally{file.delete();}
    }
    @Test public void excelKeepsEmptyColumnAndExplainsFormulaResults()throws Exception{
        String sheet="<worksheet><sheetData><row><c r='A1'><v>10</v></c><c r='C1'><f>SUM(A1:A2)</f><v>20</v></c></row></sheetData></worksheet>";
        File file=generate(".xlsx","xl/worksheets/sheet1.xml",sheet);
        try{
            Intent intent=new Intent(app,AttachmentPreviewActivity.class);
            intent.setData(Uri.fromFile(file));intent.putExtra("display_name","Синтетическая таблица.xlsx");
            screen.launchActivity(intent);awaitText("A1: 10\t\tC1: 20");awaitText("Формулы не пересчитываются");
            try(ParcelFileDescriptor p=instrumentation.getUiAutomation().executeShellCommand(
                "screencap -p /sdcard/Download/ta_office_xlsx_gap.png");
                FileInputStream stream=new FileInputStream(p.getFileDescriptor())){
                byte[] buffer=new byte[256];while(stream.read(buffer)!=-1){}
            }
        }finally{file.delete();}
    }
    @Test public void excelSharedStringIsShownOnAndroid()throws Exception{
        File valid=File.createTempFile("synthetic-shared-", ".xlsx",app.getCacheDir());
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(valid))){
            zip.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zip.write("<sst><si><t>Синтетическая общая строка</t></si></sst>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write("<worksheet><sheetData><row><c r='A1' t='s'><v>0</v></c></row></sheetData></worksheet>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        try{
            Intent intent=new Intent(app,AttachmentPreviewActivity.class);
            intent.setData(Uri.fromFile(valid));intent.putExtra("display_name","Общие строки.xlsx");
            screen.launchActivity(intent);awaitText("Синтетическая общая строка");
        }finally{valid.delete();}
    }
    @Test public void excelMissingSharedStringShowsErrorInsteadOfIndex()throws Exception{
        String sheet="<worksheet><sheetData><row><c r='A1' t='s'><v>99</v></c></row></sheetData></worksheet>";
        File file=generate(".xlsx","xl/worksheets/sheet1.xml",sheet);
        try{
            Intent intent=new Intent(app,AttachmentPreviewActivity.class);
            intent.setData(Uri.fromFile(file));intent.putExtra("display_name","Повреждённая таблица.xlsx");
            screen.launchActivity(intent);awaitText("Не удалось открыть");awaitText("Общая строка таблицы отсутствует");
        }finally{file.delete();}
    }
    @Test public void sevenZOpensNestedDocxAndSystemBackReturnsToArchive()throws Exception{
        String document="<w:document xmlns:w='urn:w'><w:body><w:p><w:r><w:t>Документ внутри 7z</w:t></w:r></w:p></w:body></w:document>";
        File doc=generate(".docx","word/document.xml",document);
        File archive=File.createTempFile("synthetic-docx-", ".7z",app.getCacheDir());
        try{
            try(org.apache.commons.compress.archivers.sevenz.SevenZOutputFile out=
                    new org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(archive);
                InputStream in=new FileInputStream(doc)){
                org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry entry=
                    out.createArchiveEntry(doc,"Документы/Учёба/Работа.docx");
                out.putArchiveEntry(entry);byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                out.closeArchiveEntry();out.finish();
            }
            Intent intent=new Intent(app,AttachmentPreviewActivity.class);
            intent.setData(Uri.fromFile(archive));intent.putExtra("display_name","Учёба.7z");
            screen.launchActivity(intent);awaitText("Документы/Учёба/Работа.docx");
            instrumentation.runOnMainSync(()->assertTrue(clickText(
                screen.getActivity().findViewById(android.R.id.content),"Документы/Учёба/Работа.docx")));
            awaitText("Документ внутри 7z");instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            awaitText("Вложений: 1");assertFalse(screen.getActivity().isFinishing());
        }finally{archive.delete();doc.delete();}
    }
    @Test public void longArchiveMemberNameWrapsWithoutClipping()throws Exception{
        String name="Документы/Очень длинное название учебного материала с важными сведениями "+
            "и подробными пояснениями для просмотра на мобильном телефоне.txt";
        File archive=generate(".zip",name,"Synthetic");
        try{
            Intent intent=new Intent(app,AttachmentPreviewActivity.class);
            intent.setData(Uri.fromFile(archive));intent.putExtra("display_name","Длинные имена.zip");
            screen.launchActivity(intent);awaitText("подробными пояснениями");
            instrumentation.runOnMainSync(()->{
                android.widget.Button button=findButton(screen.getActivity().findViewById(android.R.id.content),"подробными пояснениями");
                assertNotNull(button);android.text.Layout layout=button.getLayout();assertNotNull(layout);
                assertTrue("Long name must wrap",layout.getLineCount()>1);
                for(int line=0;line<layout.getLineCount();line++)assertEquals("No ellipsis",0,layout.getEllipsisCount(line));
                assertTrue("All text lines must fit inside the button",
                    layout.getLineBottom(layout.getLineCount()-1)<=button.getHeight()-button.getCompoundPaddingTop()-button.getCompoundPaddingBottom());
            });
        }finally{archive.delete();}
    }
}
