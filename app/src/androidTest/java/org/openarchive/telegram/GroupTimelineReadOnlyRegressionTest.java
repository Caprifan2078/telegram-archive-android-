package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** A combined timeline must never mutate archive source databases or coalesce distinct messages. */
@RunWith(AndroidJUnit4.class)
public final class GroupTimelineReadOnlyRegressionTest {
    private String key(){return UUID.randomUUID().toString().replace("-","");}
    private long add(MessageDb db,String date,String body){
        SQLiteDatabase writable=db.getWritableDatabase();
        long id=db.stage(writable,"Вася",date,body,null,"");
        db.publishStaged(writable);
        return id;
    }
    private void populate(MessageDb db,String[] dates,String[] texts){
        SQLiteDatabase writable=db.getWritableDatabase();
        for(int i=0;i<dates.length;i++)db.stage(writable,"Вася",dates[i],texts[i],null,"");
        db.publishStaged(writable);
    }
    @Test public void interleavesSameDayFromTwoSourcesWithoutDeduplicatingOrMovingPins()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String first=key(),second=key();
        MessageDb a=new MessageDb(ctx,first),b=new MessageDb(ctx,second);
        try{
            populate(a,new String[]{"23.11.2026 09:00:00","23.11.2026 11:00:00"},
                new String[]{"Одинаковый текст","Позже в Telegram"});
            populate(b,new String[]{"23.11.2026 10:00:00","23.11.2026 12:00:00"},
                new String[]{"Одинаковый текст","Второй аккаунт"});
            assertTrue(a.togglePinned(1));
            List<GroupTimeline.Source> sources=Arrays.asList(
                new GroupTimeline.Source(first,"Telegram"),
                new GroupTimeline.Source(second,"Другой аккаунт"));
            GroupTimeline.Result result=GroupTimeline.read(ctx,sources,30);
            assertEquals(4,result.items.size());
            assertEquals(second,result.items.get(0).archiveKey);
            assertEquals("Второй аккаунт",result.items.get(0).preview);
            assertEquals(first,result.items.get(1).archiveKey);
            assertEquals(second,result.items.get(2).archiveKey);
            assertEquals(first,result.items.get(3).archiveKey);
            assertEquals("Одинаковый текст",result.items.get(2).preview);
            assertEquals("Одинаковый текст",result.items.get(3).preview);
            assertEquals(0,result.invalidDates);
            assertEquals(0,result.missingSources);
            assertFalse(result.hasMore);
            assertTrue("Read-only timeline must preserve pinned message",a.isPinned(1));
        }finally{a.close();b.close();ctx.deleteDatabase("telegram_"+first+".db");ctx.deleteDatabase("telegram_"+second+".db");}
    }
    @Test public void onlyLastNPerSourceAreLoadedAndInvalidDatesAreExplicit()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String first=key();
        MessageDb a=new MessageDb(ctx,first);
        try{
            populate(a,new String[]{"01.01.2025 10:00:00","bad date","03.01.2025 12:00:00"},
                new String[]{"старое","плохая дата","новое"});
            GroupTimeline.Result lastTwo=GroupTimeline.read(ctx,
                Arrays.asList(new GroupTimeline.Source(first,"Telegram")),2);
            assertEquals(1,lastTwo.items.size());
            assertEquals("новое",lastTwo.items.get(0).preview);
            assertEquals(1,lastTwo.invalidDates);
            assertTrue(lastTwo.hasMore);
            GroupTimeline.Result full=GroupTimeline.read(ctx,
                Arrays.asList(new GroupTimeline.Source(first,"Telegram")),3);
            assertEquals(2,full.items.size());
            assertEquals(1,full.invalidDates);
            assertFalse(full.hasMore);
        }finally{a.close();ctx.deleteDatabase("telegram_"+first+".db");}
    }
    @Test public void missingArchiveIsFlaggedWithoutCreatingEmptyDatabase()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String absent=key();
        java.io.File expected=ctx.getDatabasePath("telegram_"+absent+".db");
        assertFalse(expected.exists());
        GroupTimeline.Result report=GroupTimeline.read(ctx,
            Arrays.asList(new GroupTimeline.Source(absent,"Нет файла")),30);
        assertEquals(1,report.missingSources);
        assertTrue(report.items.isEmpty());
        assertFalse("Read-only operation must not create missing SQLite",expected.exists());
        try{
            new GroupTimeline.Source("../escape","Fake");
            fail("Archive ID must be strictly validated");
        }catch(IllegalArgumentException ok){}
    }
}
