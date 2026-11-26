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
    private static void logMemory(Context context,String stage){
        android.app.ActivityManager manager=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo memory=new android.app.ActivityManager.MemoryInfo();manager.getMemoryInfo(memory);
        android.os.Debug.MemoryInfo process=new android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(process);
        android.util.Log.i("ModelSmoke","Memory "+stage+" availableMiB="+memory.availMem/1048576+" totalMiB="+memory.totalMem/1048576+" pssMiB="+process.getTotalPss()/1024+" lowMemory="+memory.lowMemory);
    }
    @Test public void downloadedModelAnswersSyntheticChatQuestion() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="model_smoke_"+UUID.randomUUID().toString().replace("-","");
        File modelDir=new File(context.getCacheDir(),"model-smoke-"+UUID.randomUUID());
        assertTrue(modelDir.mkdirs());
        String modelKey=InstrumentationRegistry.getArguments().getString("analystModel","qwen06");
        assertTrue(modelKey.matches("[a-z0-9]+"));
        File model=new File(modelDir,"analyst-"+modelKey+".litertlm");
        MessageDb db=new MessageDb(context,key);
        try {
            SQLiteDatabase sql=db.getWritableDatabase();
            db.stage(sql,"Аня","2026-02-01 10:00","Давай завтра встретимся.",null,"");
            long evidence=db.stage(sql,"Борис","2026-02-01 10:02","Договорились, встреча в 17:45.",null,"");
            db.stage(sql,"Аня","2026-02-01 10:03","Хорошо, до встречи.",null,"");
            db.publishStaged(sql);
            AtomicBoolean cancel=new AtomicBoolean(false);
            String requested=modelKey;
            AnalystModelCatalog.Model chosen=null;
            for(AnalystModelCatalog.Model item:AnalystModelCatalog.MODELS)if(item.key.equals(requested))chosen=item;
            if("qwen25unc15".equals(requested)){
                android.os.Bundle args=InstrumentationRegistry.getArguments();
                String revision=args.getString("probeRevision",""),sha=args.getString("probeSha","");
                assertTrue(revision.matches("[a-f0-9]{40}"));assertTrue(sha.matches("[a-f0-9]{64}"));
                chosen=new AnalystModelCatalog.Model(requested,"Qwen2.5 1.5B modified","QA only",
                    "https://huggingface.co/Shaurya2020/Qwen2.5-1.5B-Instruct-uncensored-litert/resolve/"+revision+"/Qwen2.5-1.5B-Instruct-uncensored_dynamic_wi8_afp32_ekv1280.litertlm",1802859440L,sha);
                android.util.Log.i("ModelSmoke","Candidate revision="+revision+" sha256="+sha);
            }
            assertNotNull("Unknown pinned model: "+requested,chosen);
            final AnalystModelCatalog.Model spec=chosen;
            android.util.Log.i("ModelSmoke","Model "+spec.key+" bytes="+spec.size);
            long downloadStart=android.os.SystemClock.elapsedRealtime();
            Class<?> sampler=Class.forName("com.google.ai.edge.litertlm.SamplerConfig");
            assertEquals("Minified APK must keep the native sampler getter",int.class,sampler.getMethod("getTopK").getReturnType());
            android.util.Log.i("ModelSmoke","Stage: JNI ABI verified; downloading pinned weights");
            AnalystModelDownload.fetch(spec,model,cancel,n->{
                if(n/(64*1024*1024)!=(n-2*1024*1024)/(64*1024*1024))
                    android.util.Log.i("ModelSmoke","Downloaded "+n+" bytes");
            });
            assertTrue("Verified model file missing",model.isFile());
            assertEquals("Model byte length differs",spec.size,model.length());
            assertTrue("Pinned file failed preflight recheck",AnalystModelDownload.matches(model,spec.size,spec.sha,cancel));
            android.util.Log.i("ModelSmoke","Verified download ms="+(android.os.SystemClock.elapsedRealtime()-downloadStart));
            android.util.Log.i("ModelSmoke","Stage: verified weights, retrieving evidence");
            DeepChatContext selected=DeepChatContext.read(context.getDatabasePath(DeepChatContext.dbName(key)),"Встреча",cancel);
            assertTrue("Actual evidence missing from prompt",selected.sources.stream().anyMatch(x->x.id==evidence));
            String prompt=selected.prompt("Во сколько назначили встречу? Укажи время цифрами и номер источника.");
            assertTrue(DeepChatContext.sentSource(prompt,evidence));
            android.util.Log.i("ModelSmoke","Stage: first native generation");
            logMemory(context,"before-single");
            long inferenceStart=android.os.SystemClock.elapsedRealtime();
            String answer=(String)Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                .getMethod("generate",String.class,String.class).invoke(null,model.getAbsolutePath(),prompt);
            android.util.Log.i("ModelSmoke","Single question ms="+(android.os.SystemClock.elapsedRealtime()-inferenceStart));
            logMemory(context,"after-single");
            assertNotNull("Native model returned null",answer);
            android.util.Log.i("ModelSmoke","Synthetic answer: "+DeepChatContext.clip(answer,2000));
            assertFalse("Native model returned empty text",answer.trim().isEmpty());
            java.util.ArrayList<String> defects=new java.util.ArrayList<>();
            if(!answer.contains("17:45"))defects.add("single: incorrect time");
            if(!answer.contains("[#"+evidence+"]"))defects.add("single: missing evidence");
            if(!DeepChatContext.unknownCitations(answer,prompt).isEmpty())defects.add("single: invented source");
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
            logMemory(context,"before-comparison");
            long comparisonStart=android.os.SystemClock.elapsedRealtime();
            String comparison=(String)Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                .getMethod("generate",String.class,String.class).invoke(null,model.getAbsolutePath(),comparisonPrompt);
            android.util.Log.i("ModelSmoke","Comparison ms="+(android.os.SystemClock.elapsedRealtime()-comparisonStart));
            logMemory(context,"after-comparison");
            assertNotNull(comparison);
            android.util.Log.i("ModelSmoke","Synthetic comparison: "+DeepChatContext.clip(comparison,2000));
            if(!comparison.contains("17:45")||!comparison.contains("09:30"))defects.add("comparison: incorrect time");
            if(!dual.citesBoth(comparison,comparisonPrompt))defects.add("comparison: missing evidence from either side");
            if(!DeepChatContext.unknownCitations(comparison,comparisonPrompt).isEmpty())defects.add("comparison: invented source");
            android.util.Log.i("ModelSmoke","Quality defects: "+defects);
            assertTrue("Model quality gate: "+defects,defects.isEmpty());
        } finally {
            db.close();context.deleteDatabase(DeepChatContext.dbName(key));model.delete();modelDir.delete();
        }
    }
}
