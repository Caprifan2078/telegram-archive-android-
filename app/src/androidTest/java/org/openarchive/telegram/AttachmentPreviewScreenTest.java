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

}
