package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Two-phase test on an isolated emulator only. No actual archive or private key. */
public class SignedUpgradeSentinelTest {
    @Test public void syntheticDataSurvivesSignedUpgrade() throws Exception{
        String phase=InstrumentationRegistry.getArguments().getString("upgradePhase","");
        Assume.assumeTrue("seed".equals(phase)||"verify".equals(phase));
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences pref=ctx.getSharedPreferences("upgrade_synthetic",Context.MODE_PRIVATE);
        MessageDb db=new MessageDb(ctx,"qa_upgrade_synthetic");
        SavedStore notes=new SavedStore(ctx);
        File imageDir=new File(ctx.getFilesDir(),"personal_images");
        File image=new File(imageDir,"qa_upgrade_synthetic.jpg");
        byte[] expectedBytes=new byte[]{(byte)0xff,(byte)0xd8,10,20,(byte)0xff,(byte)0xd9};
        try{
            if("seed".equals(phase)){
                SQLiteDatabase sql=db.getWritableDatabase();db.clearStage(sql);
                long id=db.stage(sql,"Synthetic","2024-02-29 12:00","preserve upgrade",null,"");
                db.publishStaged(sql);db.toggleFavorite(id);
                db.saveLocalProfile("synthetic","@local","synthetic");
                long note=notes.add("Synthetic note kept across upgrade",null,null,null);
                long quote=notes.addQuote("Synthetic quoted note","qa_upgrade_synthetic",id);
                if(!imageDir.isDirectory()&&!imageDir.mkdirs())throw new java.io.IOException("Image directory unavailable");
                try(FileOutputStream output=new FileOutputStream(image)){output.write(expectedBytes);output.getFD().sync();}
                assertTrue(pref.edit().putLong("id",id).putLong("note",note)
                    .putLong("quote",quote).putString("setting","preserved").commit());
            }else{
                long id=pref.getLong("id",-1);assertTrue(id>0);
                assertEquals("preserved",pref.getString("setting",""));
                assertEquals(1,db.totalMessages());assertTrue(db.isFavorite(id));
                assertEquals("@local",db.localProfile()[1]);
                assertEquals("preserve upgrade",db.page(0,1,"",false,"",false,false).get(0).body);
                List<SavedStore.Note> stored=notes.recent(20);
                boolean foundNote=false,foundQuote=false;
                for(SavedStore.Note n:stored){
                    if(n.id==pref.getLong("note",-1))
                        foundNote="Synthetic note kept across upgrade".equals(n.body);
                    if(n.id==pref.getLong("quote",-1))
                        foundQuote="Synthetic quoted note".equals(n.body)
                            &&"qa_upgrade_synthetic".equals(n.originArchive)
                            &&n.originMessageId==id;
                }
                assertTrue("Personal note must survive failed/successful upgrades",foundNote);
                assertTrue("Quote origin must survive failed/successful upgrades",foundQuote);
                assertTrue("Personal image must remain present",image.isFile());
                byte[] actual=new byte[expectedBytes.length];
                try(FileInputStream input=new FileInputStream(image)){
                    assertEquals(actual.length,input.read(actual));
                    assertEquals(-1,input.read());
                }
                assertArrayEquals("Original image bytes must survive",expectedBytes,actual);
            }
        }finally{db.close();notes.close();}
    }
}
