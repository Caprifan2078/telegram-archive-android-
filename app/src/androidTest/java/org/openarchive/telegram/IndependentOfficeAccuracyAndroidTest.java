package org.openarchive.telegram;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** SAX behavior can differ on Android Expat versus desktop Java. */
@RunWith(AndroidJUnit4.class)
public class IndependentOfficeAccuracyAndroidTest {
    @Test public void excelRowNumberAndMissingStringReferenceOnAndroid()throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)){
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(("<worksheet><sheetData><row r='1'><c r='A1'><v>5</v></c></row>"+
                "<row r='5'><c r='A5'><v>9</v></c></row></sheetData></worksheet>")
                .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        String view=OfficePreviewText.render(new ByteArrayInputStream(bytes.toByteArray()),"a.xlsx");
        assertTrue(view,view.contains("пропущено строк: 3"));
        assertTrue(view,view.contains("Строка 5: 9"));
    }
}
