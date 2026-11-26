package org.openarchive.telegram;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

/** Tests the actual separate-process screen with no private archive or downloaded model. */
public class DeepAnalystOfflineUiTest {
    @Test public void relationshipQuestionsAndEvidenceWorkWithoutDownloadedModel(){
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String key="analyst_ui_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(ctx,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            db.stage(sql,"Аня","2026-01-01 10:00","Я люблю тебя",null,"");
            db.stage(sql,"Борис","2026-01-01 10:01","Спасибо",null,"");db.publishStaged(sql);
            db.close();
            ctx.startActivity(new Intent(ctx,DeepAnalystActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("archive",key).putExtra("relationships",true)
                .putExtra("question","Какие признания в любви встречались?"));
            assertNotNull(device.wait(Until.findObject(By.text("Взаимоотношения")),10000));
            assertNotNull(device.wait(Until.findObject(By.text("80 вопросов об отношениях  ›")),5000));
            UiObject2 ask=device.wait(Until.findObject(By.text("Найти источники")),5000);
            assertNotNull("Offline evidence action must be usable",ask);ask.click();
            UiObject2 answer=device.wait(Until.findObject(By.text("Подборка источников · без нейросети")),10000);
            assertNotNull("Results must be revealed without scrolling through model settings",answer);
            assertTrue(answer.getVisibleBounds().height()>0);
        }finally{
            device.pressBack();db.close();ctx.deleteDatabase(DeepChatContext.dbName(key));
        }
    }
}
