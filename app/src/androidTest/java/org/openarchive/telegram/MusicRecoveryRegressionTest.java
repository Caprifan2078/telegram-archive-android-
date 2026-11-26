package org.openarchive.telegram;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class MusicRecoveryRegressionTest {
    private static Object field(MusicPlayerActivity a,String name){
        try{Field f=MusicPlayerActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
        catch(Exception e){throw new AssertionError(e);}
    }
    private static void waitFor(ActivityScenario<MusicPlayerActivity> scenario,Predicate<MusicPlayerActivity> condition){
        AtomicBoolean ready=new AtomicBoolean();
        long deadline=SystemClock.uptimeMillis()+18000;
        while(!ready.get()&&SystemClock.uptimeMillis()<deadline){
            scenario.onActivity(a->ready.set(condition.test(a)));
            if(!ready.get())SystemClock.sleep(100);
        }
        assertTrue("Music player did not reach expected state",ready.get());
    }
    private static void le16(OutputStream o,int v)throws IOException{o.write(v&255);o.write((v>>8)&255);}
    private static void le32(OutputStream o,int v)throws IOException{le16(o,v);le16(o,v>>16);}
    private static void wav(File file)throws IOException{
        int rate=16000,frames=rate*8;
        try(OutputStream o=new BufferedOutputStream(new FileOutputStream(file))){
            o.write(new byte[]{'R','I','F','F'});le32(o,36+frames*2);
            o.write(new byte[]{'W','A','V','E','f','m','t',' '});le32(o,16);
            le16(o,1);le16(o,1);le32(o,rate);le32(o,rate*2);le16(o,2);le16(o,16);
            o.write(new byte[]{'d','a','t','a'});le32(o,frames*2);
            for(int i=0;i<frames;i++)le16(o,0);
        }
    }
    @Test public void missingTrackCanBeRetriedAfterOriginalFileReturns()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="musicretry"+UUID.randomUUID().toString().replace("-","");
        File directory=new File(ctx.getFilesDir(),"telegram_imports/"+key);
        assertTrue(directory.mkdirs());
        File file=new File(directory,"test.wav");
        MessageDb db=new MessageDb(ctx,key);
        long id;
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            long msg=db.stage(sql,"QA","2026-11-26 10:00:00","Offline music",null,"");
            db.stageAttachment(sql,msg,"test.wav","audio");
            db.publishStaged(sql);
            try(Cursor c=sql.rawQuery("SELECT id FROM attachments WHERE kind='audio'",null)){
                assertTrue(c.moveToFirst());id=c.getLong(0);
            }
        }finally{db.close();}
        Intent intent=new Intent(ctx,MusicPlayerActivity.class)
            .putExtra("chat_key",key).putExtra("tree","local:"+directory.getAbsolutePath())
            .putExtra("attachment_id",id);
        try(ActivityScenario<MusicPlayerActivity> scenario=ActivityScenario.launch(intent)){
            waitFor(scenario,a->((TextView)field(a,"notice")).getText().toString().contains("Файл отсутствует"));
            scenario.onActivity(a->{
                assertFalse(((SeekBar)field(a,"seek")).isEnabled());
                assertTrue(((Button)field(a,"toggle")).isEnabled());
                assertEquals("Повторить",((Button)field(a,"toggle")).getText().toString());
            });
            wav(file);
            scenario.onActivity(a->((Button)field(a,"toggle")).performClick());
            waitFor(scenario,a->(Boolean)field(a,"prepared"));
            scenario.onActivity(a->{
                assertTrue(((SeekBar)field(a,"seek")).isEnabled());
                assertTrue(((SeekBar)field(a,"seek")).getMax()>=7000);
            });
            assertTrue("Original audio must remain untouched",file.isFile());
        }finally{file.delete();directory.delete();ctx.deleteDatabase("telegram_"+key+".db");}
    }
}
