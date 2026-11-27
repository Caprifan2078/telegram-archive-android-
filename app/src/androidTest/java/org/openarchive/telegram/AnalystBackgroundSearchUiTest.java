package org.openarchive.telegram;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnalystBackgroundSearchUiTest {
    @Test public void backgroundSearchSurvivesLeavingAnalyst()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="qa_bg_"+java.util.UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(ctx,key);
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            db.stage(sql,"Анна","2026-01-01 10:00","Встреча в 17:45",null,"");
            db.publishStaged(sql);db.close();
            Intent intent=new Intent(ctx,DeepAnalystActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("archive",key).putExtra("question","Встреча");
            ctx.startActivity(intent);
            UiObject2 bg=device.wait(Until.findObject(By.text("Работать в фоне  ›")),15000);
            assertNotNull("Background action missing",bg);bg.click();
            UiObject2 search=device.wait(Until.findObject(By.text("Найти сообщения")),10000);
            assertNotNull("Background search missing",search);search.click();
            // Android 13+ may ask for notification permission. The job must not depend on granting it.
            UiObject2 allow=device.wait(Until.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button")),2500);
            if(allow!=null)allow.click();
            device.pressBack();device.pressHome();Thread.sleep(1200);
            ctx.startActivity(intent);
            UiObject2 result=device.wait(Until.findObject(By.textContains("Найденные сообщения · без нейросети")),20000);
            if(result==null){
                // Synthetic fixture only: capture visible tree and activity state for diagnosis.
                java.io.File dump=new java.io.File(ctx.getCacheDir(),"background-ui.xml");
                device.dumpWindowHierarchy(dump);
                android.util.Log.w("AnalystJob","failure package="+device.getCurrentPackageName()+" hierarchy="+new String(java.nio.file.Files.readAllBytes(dump.toPath()),java.nio.charset.StandardCharsets.UTF_8));
                dump.delete();
            }
            assertNotNull("Background result did not return",result);
            assertNotNull("Source must be available",device.wait(Until.findObject(By.textContains("Встреча в 17:45")),10000));
        }finally{device.pressBack();db.close();ctx.deleteDatabase(DeepChatContext.dbName(key));}
    }
}
