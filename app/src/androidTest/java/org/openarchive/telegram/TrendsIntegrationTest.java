package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

/** Instrumentation: real SQLite -> read-only timeline data. Synthetic data only. */
@RunWith(AndroidJUnit4.class)
public class TrendsIntegrationTest {
    @Test public void groupsAcrossYearsHaveCorrectBinsHourFiltersAndNoWrites()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");MessageDb db=new MessageDb(ctx,key);
        try {
            SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
            try {
                db.stage(sql,"Аня","31.12.2026 23:58","С наступающим! 🧡",null,"");
                db.stage(sql,"Борис","2027-01-01 00:01:11","Спасибо!",null,"");
                db.stage(sql,"АНЯ","2027-01-08 13:44","Когда увидимся?",null,"");
                db.stage(sql,"Вика","дата недоступна","Неизвестный час",null,"");
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            db.publishStaged(sql);
            int original=db.totalMessages();ChatAnalytics.Snapshot report=db.analytics();
            assertEquals(4,report.totalMessages);assertEquals(original,db.totalMessages());
            assertEquals(3,report.people.size());
            assertEquals(3,report.trends.all.validHour);
            assertEquals(1,report.trends.all.unknownDate);
            assertEquals(1,report.trends.all.hours[23]);
            assertEquals(1,report.trends.all.hours[0]);
            assertEquals(1,report.trends.all.hours[13]);
            ChatTrends.Series days=ChatTrends.series(report.trends.all,"day");
            assertEquals(9,days.values.length); // Dec 31 .. Jan 8 inclusive
            assertEquals(1,days.values[0]);assertEquals(1,days.values[1]);
            assertEquals(1,days.values[8]);
            ChatTrends.Series weeks=ChatTrends.series(report.trends.all,"week");
            assertEquals(2,weeks.values.length);assertEquals(2,weeks.values[0]);assertEquals(1,weeks.values[1]);
            assertEquals(1,report.trends.forPerson("аня").hours[23]);
            assertEquals(0,report.trends.forPerson("борис").hours[23]);
            assertEquals(1,report.trends.forPerson("вика").unknownDate);
            ChatTrends.YearCalendar year2026=ChatTrends.calendar(report.trends.all,2026);
            ChatTrends.YearCalendar year2027=ChatTrends.calendar(report.trends.all,2027);
            assertEquals(1,year2026.total);
            assertEquals(2,year2027.total);
            assertEquals(2,year2027.activeDays);
            assertEquals(0,ChatTrends.calendar(report.trends.forPerson("вика"),2027).total);
            ChatTrends.MonthlyComparison comparison=ChatTrends.compareMonths(
                report.trends.forPerson("аня"),report.trends.forPerson("борис"),2027,1);
            assertEquals(12,comparison.labels.size());
            assertEquals(2,comparison.firstTotal);
            assertEquals(1,comparison.secondTotal);
            assertEquals(original,db.totalMessages());
        }finally{db.close();ctx.deleteDatabase("telegram_"+key+".db");}
    }
}
