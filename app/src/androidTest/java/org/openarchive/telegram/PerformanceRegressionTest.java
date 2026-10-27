package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.Assert.*;

/** Baseline only: device-dependent observations; no arbitrary speed targets. */
@RunWith(AndroidJUnit4.class)
public class PerformanceRegressionTest {
    private long now(){return SystemClock.elapsedRealtimeNanos();}
    private static double ms(long nanos){return nanos/1000000.0;}
    private static double percentile(long[] values,double fraction){
        long[] copy=values.clone();Arrays.sort(copy);
        return ms(copy[(int)Math.ceil(fraction*copy.length)-1]);
    }
    @Test public void indexedTenThousandMessagePagingAndSearchP50P95()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(ctx,id);final int N=10000;
        try{
            SQLiteDatabase d=db.getWritableDatabase();
            long start=now();d.beginTransaction();
            try{
                db.clearStage(d);
                for(int i=0;i<N;i++)db.stage(d,"Участник "+i%3,"2026-10-23 12:00",
                    "измерение важное слово номер "+i,null,"");
                d.setTransactionSuccessful();
            }finally{d.endTransaction();}
            long stageNs=now()-start;
            start=now();db.publishStaged(d);long publishNs=now()-start;
            assertEquals(N,db.totalMessages());
            // Warm DB cache and query paths before timing.
            for(int i=0;i<8;i++){db.page(i*41,40,"",false,"",false,false);db.count("измерение",false,"",false,false);}
            long[] pages=new long[150],searches=new long[50];
            for(int i=0;i<pages.length;i++){
                long t=now();assertEquals(40,db.page((i*67)%(N-40),40,"",false,"",false,false).size());pages[i]=now()-t;
            }
            for(int i=0;i<searches.length;i++){
                long t=now();assertEquals(N,db.count("измерение",false,"",false,false));searches[i]=now()-t;
            }
            // Baseline for the new read-only analytics screen, same synthetic dataset.
            long[] analyses=new long[8];
            ChatAnalytics.Snapshot analytics=null;
            for(int i=0;i<analyses.length;i++){
                long t=now();analytics=db.analytics();analyses[i]=now()-t;
            }
            assertNotNull(analytics);assertEquals(N,analytics.totalMessages);
            assertEquals(3,analytics.people.size());
            // Cached, in-memory chart queries: 4 period granularities + 24h + heatmap.
            long[] chartTimes=new long[200];
            for(int i=0;i<chartTimes.length;i++){
                long t=now();
                ChatTrends.Activity a=analytics.trends.forPerson(i%2==0?"":"участник 1");
                ChatTrends.Series bins=ChatTrends.series(a,i%4==0?"day":i%4==1?"week":i%4==2?"month":"year");
                assertTrue(bins.values.length>0);
                assertEquals(24,a.hours.length);
                assertEquals(7,a.weekdayHours.length);
                chartTimes[i]=now()-t;
            }
            // New calendar and comparisons, measured independently from DB scans.
            long[] calendarTimes=new long[120],comparisonTimes=new long[120];
            for(int i=0;i<120;i++){
                ChatTrends.Activity a=analytics.trends.forPerson(i%2==0?"":"участник 1");
                long startCalendar=now();
                ChatTrends.YearCalendar grid=ChatTrends.calendar(a,2026);
                calendarTimes[i]=now()-startCalendar;
                assertTrue(grid.weekCount>=52&&grid.weekCount<=54);
                long startComparison=now();
                ChatTrends.MonthlyComparison chart=ChatTrends.compareMonths(
                    analytics.trends.forPerson("участник 1"),
                    analytics.trends.forPerson("участник 2"),2026,10);
                comparisonTimes[i]=now()-startComparison;
                assertEquals(12,chart.first.length);
            }
            Runtime runtime=Runtime.getRuntime();long heapBytes=runtime.totalMemory()-runtime.freeMemory();
            Log.i("TG_ARCHIVE_BENCH","fixture=synthetic_10000_messages; N="+N+
                "; staging_ms="+ms(stageNs)+"; index_publish_ms="+ms(publishNs)+
                "; page_p50_ms="+percentile(pages,.50)+"; page_p95_ms="+percentile(pages,.95)+
                "; search_p50_ms="+percentile(searches,.50)+"; search_p95_ms="+percentile(searches,.95)+
                "; analytics_p50_ms="+percentile(analyses,.50)+"; analytics_p95_ms="+percentile(analyses,.95)+
                "; analytics_words="+analytics.totalWords+
                "; chart_p50_ms="+percentile(chartTimes,.50)+
                "; chart_p95_ms="+percentile(chartTimes,.95)+
                "; calendar_p50_ms="+percentile(calendarTimes,.50)+
                "; calendar_p95_ms="+percentile(calendarTimes,.95)+
                "; compare_p50_ms="+percentile(comparisonTimes,.50)+
                "; compare_p95_ms="+percentile(comparisonTimes,.95)+
                "; java_heap_used_bytes="+heapBytes);
        }finally{db.close();ctx.deleteDatabase("telegram_"+id+".db");}
    }
    @Test public void importsTenThousandActualHtmlMessagesAndSearchesBoundaryRecords()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");final int count=10000;
        java.io.File folder=new java.io.File(ctx.getCacheDir(),"synthetic-html-"+key);
        assertTrue(folder.mkdirs());java.io.File html=new java.io.File(folder,"messages.html");
        MessageDb db=new MessageDb(ctx,key);
        try{
            try(java.io.Writer out=new java.io.BufferedWriter(new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(html),java.nio.charset.StandardCharsets.UTF_8))){
                out.write("<div class='history'>");
                for(int i=1;i<=count;i++){
                    out.write("<div class='message default' id='message"+i+"'><div class='body'>");
                    out.write("<div class='from_name'>Synthetic Alice</div>");
                    out.write("<div class='date details' title='2024-02-29 12:00:00'></div>");
                    out.write("<div class='text'>syntheticmessage"+i+"</div></div></div>");
                }
                out.write("</div>");
            }
            long started=now();
            int imported=TelegramImporter.importFiles(ctx.getContentResolver(),
                new DocumentFolder(ctx.getContentResolver(),folder),db,ignored->{});
            long elapsed=now()-started;
            assertEquals(count,imported);assertEquals(count,db.totalMessages());
            assertEquals("syntheticmessage1",db.page(0,1,"",false,"",false,false).get(0).body);
            assertEquals("syntheticmessage10000",db.page(count-1,1,"",false,"",false,false).get(0).body);
            assertEquals(1,db.count("syntheticmessage10000",false,"",false,false));
            assertEquals(count,db.analytics().totalMessages);
            Log.i("TG_ARCHIVE_BENCH","fixture=synthetic_real_html_10000; bytes="+html.length()+
                "; import_ms="+ms(elapsed)+"; java_heap_used_bytes="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
        }finally{
            db.close();ctx.deleteDatabase("telegram_"+key+".db");ArchiveFileImport.deleteTree(folder);
        }
    }
}
