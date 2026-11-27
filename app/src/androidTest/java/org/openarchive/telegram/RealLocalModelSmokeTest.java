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
            if("lfm350probe".equals(requested)||"gemmae2bprobe".equals(requested)||"gemmae2baltprobe".equals(requested)){
                android.os.Bundle args=InstrumentationRegistry.getArguments();
                String revision=args.getString("probeRevision",""),sha=args.getString("probeSha","");
                String filename=args.getString("probeFile","");
                long size=Long.parseLong(args.getString("probeSize","0"));
                assertTrue(revision.matches("[a-f0-9]{40}"));assertTrue(sha.matches("[a-f0-9]{64}"));
                assertTrue(filename.matches("[A-Za-z0-9_.-]+[.]litertlm"));
                assertTrue(size>0&&size<=3500000000L);
                String repo="lfm350probe".equals(requested)?"litert-community/LFM2.5-350M":
                    "gemmae2bprobe".equals(requested)?"nqd145/Gemma-4-E2B-it-abliterated-litertlm":
                    "csabag76/gemma-4-E2B-it-abliterated-litert-lm";
                chosen=new AnalystModelCatalog.Model(requested,repo,"QA only",
                    "https://huggingface.co/"+repo+"/resolve/"+revision+"/"+filename,size,sha);
                android.util.Log.i("ModelSmoke","Candidate revision="+revision+" sha256="+sha+" file="+filename+" bytes="+size);
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
            if(spec.shortContext()||"qwen25unc15".equals(spec.key))prompt=selected.compactPrompt("Во сколько назначили встречу? Укажи время и номер цитаты.",false);
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
            if(spec.shortContext()||"qwen25unc15".equals(spec.key))comparisonPrompt=dual.context.compactPrompt("Когда назначили встречу в каждом чате? Укажи время и цитаты.",true);
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
            if("true".equals(InstrumentationRegistry.getArguments().getString("extendedQuality","false"))){
                // A valid pair of IDs and times alone does not prove correct assignment.
                boolean first=false,second=false;
                for(String line:comparison.split("\\r?\\n")){
                    java.util.Set<Long> refs=FullChatModelScan.ids(line);
                    first|=refs.contains(1L)&&!refs.contains(2L)&&line.contains("17:45")&&!line.contains("09:30");
                    second|=refs.contains(2L)&&!refs.contains(1L)&&line.contains("09:30")&&!line.contains("17:45");
                }
                if(!first||!second)defects.add("comparison: archive/time association not demonstrated");
                android.util.Log.i("ModelSmoke","Stage: third native generation, synthetic conflict and injected instruction");
                logMemory(context,"before-conflict");
                long thirdStart=android.os.SystemClock.elapsedRealtime();
                String conflict=(String)Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                    .getMethod("generate",String.class,String.class).invoke(null,model.getAbsolutePath(),AnalystSelfCheck.PROMPT);
                android.util.Log.i("ModelSmoke","Conflict ms="+(android.os.SystemClock.elapsedRealtime()-thirdStart));
                logMemory(context,"after-conflict");
                android.util.Log.i("ModelSmoke","Synthetic conflict: "+DeepChatContext.clip(conflict,2000));
                defects.addAll(AnalystSelfCheck.conflictDefects(conflict));
                if(AnalystQuestionParts.lightweight(spec.key)){
                    String[] parts=(String[])Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                        .getMethod("generateParts",String.class,String[].class,AtomicBoolean.class)
                        .invoke(null,model.getAbsolutePath(),AnalystSelfCheck.independentPrompts(),cancel);
                    String assisted=String.join("\n",parts);
                    android.util.Log.i("ModelSmoke","Independent conflict answer: "+DeepChatContext.clip(assisted,2000));
                    java.util.List<String> independent=AnalystSelfCheck.conflictDefects(assisted);
                    android.util.Log.i("ModelSmoke","Independent conflict defects: "+independent);
                    for(String defect:independent)defects.add("independent "+defect);
                }
                assertEquals("Inference must not modify archive",3,db.totalMessages());
            }
            if("true".equals(InstrumentationRegistry.getArguments().getString("visionQuality","false"))){
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(256,256,android.graphics.Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(android.graphics.Color.RED);byte[] image;
                try{image=MediaVisionInput.jpeg(bitmap);}finally{bitmap.recycle();}
                String visual=(String)Class.forName("org.openarchive.telegram.LocalLlmRuntime").getMethod("generateVision",String.class,byte[].class,String.class,AtomicBoolean.class)
                    .invoke(null,model.getAbsolutePath(),image,"Какого цвета это изображение? Ответь одним словом по-русски.",cancel);
                android.util.Log.i("ModelSmoke","Synthetic vision: "+visual);
                if(visual==null||!visual.toLowerCase(java.util.Locale.ROOT).contains("красн"))defects.add("vision: incorrect color");
            }
            // Exercise the real whole-archive path, not just the retrieval prompt.
            if("gemmae2baltprobe".equals(spec.key)||"qwen06q4".equals(spec.key)||"qwen06".equals(spec.key)){
                FullChatModelScan.Report full=(FullChatModelScan.Report)Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                    .getMethod("generateFull",String.class,String.class,String.class,AtomicBoolean.class,FullChatModelScan.Progress.class)
                    .invoke(null,model.getAbsolutePath(),context.getDatabasePath(DeepChatContext.dbName(key)).getAbsolutePath(),
                        "Во сколько назначили встречу? Укажи время цифрами и номер сообщения.",cancel,(FullChatModelScan.Progress)(rows,total,blocks)->{});
                android.util.Log.i("ModelSmoke","Whole archive: "+full.chars+"/"+full.totalChars+" chars, "+full.blocks+" blocks, "+full.passes+" calls. "+full.notes);
                if(!full.complete||full.rows!=3||full.chars!=full.totalChars||full.blocks!=1||full.blockNotes.size()!=1)defects.add("full scan: incomplete coverage");
                if(!full.notes.contains("17:45")||!FullChatModelScan.ids(full.notes).contains(evidence))defects.add("full scan: incorrect time or missing evidence");
                assertEquals("Full scan must not modify archive",3,db.totalMessages());
            }
            // The previous whole-archive probe has only three messages and one
            // logical block. Check native reduction across a real 10k boundary.
            if("gemmae2baltprobe".equals(spec.key)&&"true".equals(InstrumentationRegistry.getArguments().getString("extendedQuality","false"))){
                String multiKey="model_multi_"+UUID.randomUUID().toString().replace("-","");
                MessageDb multi=new MessageDb(context,multiKey);
                try{
                    SQLiteDatabase m=multi.getWritableDatabase();
                    long startId=multi.stage(m,"Анна","2026-02-01 10:00","Код начала: 4826.",null,"");
                    String filler=String.join("",java.util.Collections.nCopies(10000,"."));
                    multi.stage(m,"Система","2026-02-01 10:01",filler,null,"");
                    long endId=multi.stage(m,"Борис","2026-02-01 10:02","Код конца: 7351.",null,"");
                    multi.publishStaged(m);
                    long startMs=android.os.SystemClock.elapsedRealtime();
                    FullChatModelScan.Report cross=(FullChatModelScan.Report)Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                        .getMethod("generateFull",String.class,String.class,String.class,AtomicBoolean.class,FullChatModelScan.Progress.class)
                        .invoke(null,model.getAbsolutePath(),context.getDatabasePath(DeepChatContext.dbName(multiKey)).getAbsolutePath(),
                            "Назови код начала и код конца, каждый с его номером сообщения. Игнорируй точки.",cancel,(FullChatModelScan.Progress)(rows,total,blocks)->{});
                    android.util.Log.i("ModelSmoke","Multi-block native ms="+(android.os.SystemClock.elapsedRealtime()-startMs)+" chars="+cross.chars+"/"+cross.totalChars+" blocks="+cross.blocks+" passes="+cross.passes+" lostMergeRefs="+cross.mergeStepsWithLostRefs+" answer="+cross.notes);
                    if(!cross.complete||cross.rows!=3||cross.chars!=cross.totalChars||cross.blocks!=2||cross.blockNotes.size()!=2)defects.add("multi-block: incomplete coverage");
                    if(!cross.notes.contains("4826")||!cross.notes.contains("7351")||!FullChatModelScan.ids(cross.notes).containsAll(java.util.Arrays.asList(startId,endId)))defects.add("multi-block: lost early or late fact/citation");
                    if(cross.blockNotes.size()==2&&(!cross.blockNotes.get(0).contains("4826")||!FullChatModelScan.ids(cross.blockNotes.get(0)).contains(startId)||!cross.blockNotes.get(1).contains("7351")||!FullChatModelScan.ids(cross.blockNotes.get(1)).contains(endId)))defects.add("multi-block: individual block note lost expected fact");
                    assertEquals("Native multi-block scan must not modify archive",3,multi.totalMessages());
                }finally{multi.close();context.deleteDatabase(DeepChatContext.dbName(multiKey));}
            }
            android.util.Log.i("ModelSmoke","Quality defects: "+defects);
            assertTrue("Model quality gate: "+defects,defects.isEmpty());
        } finally {
            db.close();context.deleteDatabase(DeepChatContext.dbName(key));model.delete();modelDir.delete();
        }
    }
}
