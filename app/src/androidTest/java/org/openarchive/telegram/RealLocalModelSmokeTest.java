package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Actual public Qwen weights, actual Android SQLite archive and native inference.
 * A missing model, network error, native crash or wrong answer must fail the test. */
public final class RealLocalModelSmokeTest {
    @Test public void downloadedModelAnswersSyntheticChatQuestion() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="model_smoke_"+UUID.randomUUID().toString().replace("-","");
        File model=new File(context.getCacheDir(),"model-smoke-"+UUID.randomUUID()+".litertlm");
        MessageDb db=new MessageDb(context,key);
        try {
            SQLiteDatabase sql=db.getWritableDatabase();
            db.stage(sql,"Аня","2026-02-01 10:00","Давай завтра встретимся.",null,"");
            long evidence=db.stage(sql,"Борис","2026-02-01 10:02","Договорились, встреча в 17:45.",null,"");
            db.stage(sql,"Аня","2026-02-01 10:03","Хорошо, до встречи.",null,"");
            db.publishStaged(sql);
            AtomicBoolean cancel=new AtomicBoolean(false);
            AnalystModelCatalog.Model spec=AnalystModelCatalog.MODELS[0];
            assertEquals("qwen06",spec.key);
            android.util.Log.i("ModelSmoke","Stage: downloading pinned weights");
            AnalystModelDownload.fetch(spec,model,cancel,n->{
                if(n/(64*1024*1024)!=(n-2*1024*1024)/(64*1024*1024))
                    android.util.Log.i("ModelSmoke","Downloaded "+n+" bytes");
            });
            assertTrue("Verified model file missing",model.isFile());
            assertEquals("Model byte length differs",spec.size,model.length());
            assertTrue("Pinned file failed preflight recheck",AnalystModelDownload.matches(model,spec.size,spec.sha,cancel));
            android.util.Log.i("ModelSmoke","Stage: verified weights, retrieving evidence");
            DeepChatContext selected=DeepChatContext.read(context.getDatabasePath(DeepChatContext.dbName(key)),"Встреча",cancel);
            assertTrue("Actual evidence missing from prompt",selected.sources.stream().anyMatch(x->x.id==evidence));
            String prompt=selected.prompt("Во сколько назначили встречу? Укажи время цифрами и номер источника.");
            assertTrue(DeepChatContext.sentSource(prompt,evidence));
            android.util.Log.i("ModelSmoke","Stage: first native generation");
            String answer=(String)Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                .getMethod("generate",String.class,String.class).invoke(null,model.getAbsolutePath(),prompt);
            assertNotNull("Native model returned null",answer);
            android.util.Log.i("ModelSmoke","Synthetic answer: "+DeepChatContext.clip(answer,2000));
            assertFalse("Native model returned empty text",answer.trim().isEmpty());
            assertTrue("Model failed to retrieve correct time from synthetic chat",answer.contains("17:45"));
            assertTrue("Model did not cite the actual source",answer.contains("[#"+evidence+"]"));
            assertTrue("Model invented source IDs",DeepChatContext.unknownCitations(answer,prompt).isEmpty());
            assertEquals("Inference must not modify archive",3,db.totalMessages());
            // Reuse the downloaded model; verify a comparison with overlapping source IDs.
            java.util.List<DeepChatContext.Source> a=java.util.Collections.singletonList(
                new DeepChatContext.Source(1,"Аня","2026-02-01","Встреча в 17:45."));
            java.util.List<DeepChatContext.Source> b=java.util.Collections.singletonList(
                new DeepChatContext.Source(1,"Аня","2026-02-01","Встреча в 09:30."));
            DualAnalystContext dual=DualAnalystContext.combine("a",new DeepChatContext(a,1,"Одно сообщение","QA"),
                "b",new DeepChatContext(b,1,"Одно сообщение","QA"));
            String comparisonPrompt=dual.prompt("Назови время встречи в А и Б цифрами, со ссылками [#1] и [#2].");
            android.util.Log.i("ModelSmoke","Stage: second native generation");
            String comparison=(String)Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                .getMethod("generate",String.class,String.class).invoke(null,model.getAbsolutePath(),comparisonPrompt);
            assertNotNull(comparison);
            assertTrue("Comparison must mention both exact times",comparison.contains("17:45")&&comparison.contains("09:30"));
            assertTrue("Comparison must cite both archives",comparison.contains("[#1]")&&comparison.contains("[#2]"));
            assertTrue(DeepChatContext.unknownCitations(comparison,comparisonPrompt).isEmpty());
        } finally {
            db.close();context.deleteDatabase(DeepChatContext.dbName(key));model.delete();
        }
    }
}
