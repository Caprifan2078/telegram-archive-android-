package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Two-phase test on an isolated emulator only. No actual archive or private key. */
public class SignedUpgradeSentinelTest {
    @Test public void syntheticDataSurvivesSignedUpgrade(){
        String phase=InstrumentationRegistry.getArguments().getString("upgradePhase","");
        Assume.assumeTrue("seed".equals(phase)||"verify".equals(phase));
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences pref=ctx.getSharedPreferences("upgrade_synthetic",Context.MODE_PRIVATE);
        MessageDb db=new MessageDb(ctx,"qa_upgrade_synthetic");
        try{
            if("seed".equals(phase)){
                SQLiteDatabase sql=db.getWritableDatabase();db.clearStage(sql);
                long id=db.stage(sql,"Synthetic","2024-02-29 12:00","preserve upgrade",null,"");
                db.publishStaged(sql);db.toggleFavorite(id);
                db.saveLocalProfile("synthetic","@local","synthetic");
                assertTrue(pref.edit().putLong("id",id).putString("setting","preserved").commit());
            }else{
                long id=pref.getLong("id",-1);assertTrue(id>0);
                assertEquals("preserved",pref.getString("setting",""));
                assertEquals(1,db.totalMessages());assertTrue(db.isFavorite(id));
                assertEquals("@local",db.localProfile()[1]);
                assertEquals("preserve upgrade",db.page(0,1,"",false,"",false,false).get(0).body);
            }
        }finally{db.close();}
    }
}
