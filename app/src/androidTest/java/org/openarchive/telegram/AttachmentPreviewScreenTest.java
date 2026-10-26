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
    private void awaitText(String expected)throws Exception{
        long deadline=android.os.SystemClock.uptimeMillis()+9000;
        while(android.os.SystemClock.uptimeMillis()<deadline){
            instrumentation.waitForIdleSync();
            if(contains(screen.getActivity().findViewById(android.R.id.content),expected))return;
            Thread.sleep(110);
        }
        fail("Preview screen never displayed: "+expected);
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
}
