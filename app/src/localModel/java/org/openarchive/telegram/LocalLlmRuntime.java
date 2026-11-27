package org.openarchive.telegram;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.SamplerConfig;
import com.google.ai.edge.litertlm.LogSeverity;
import com.google.ai.edge.litertlm.ThinkingConfig;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in local model build; LiteRT-LM 0.18.0. CPU-first, one engine at a time. */
public final class LocalLlmRuntime {
    // Native code aborts the process on missing getters. Fail in Java instead.
    static void verifySamplerAbi(){
        try{
            java.lang.reflect.Method topK=SamplerConfig.class.getMethod("getTopK");
            if(topK.getReturnType()!=int.class||java.lang.reflect.Modifier.isStatic(topK.getModifiers()))
                throw new NoSuchMethodException("getTopK()I");
        }catch(ReflectiveOperationException bad){
            throw new IllegalStateException("LiteRT-LM JNI ABI mismatch: SamplerConfig.getTopK()I",bad);
        }
    }
    private static Engine open(String path,int contextTokens){
        verifySamplerAbi();
        Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);
        Engine engine=new Engine(new EngineConfig(path,new Backend.CPU(2,null),null,null,contextTokens,null,":nocache",null));
        try{
            engine.initialize();
            return engine;
        }catch(RuntimeException|LinkageError failure){
            try{engine.close();}catch(RuntimeException|LinkageError closeFailure){if(closeFailure!=failure)failure.addSuppressed(closeFailure);}
            throw failure;
        }
    }
    private static String infer(Engine engine,String prompt,int outputLimit,AtomicBoolean cancel,boolean preserveThinking)throws InterruptedException{
        if(cancel.get())throw new InterruptedException();
        ConversationConfig config=new ConversationConfig(null,Collections.emptyList(),Collections.emptyList(),
            new SamplerConfig(20,0.85,0.2,0),false,null,Collections.emptyMap(),null,false,outputLimit,preserveThinking?null:new ThinkingConfig(false,-1),false,false,null);
        try(Conversation conversation=engine.createConversation(config)){
            // The upstream API explicitly supports cancellation of an ongoing
            // conversation. Join the watcher before closing native resources.
            AtomicBoolean finished=new AtomicBoolean();
            Thread watcher=new Thread(()->{
                while(!finished.get()){
                    if(cancel.get()){
                        try{conversation.cancelProcess();}catch(RuntimeException|LinkageError ignored){}
                    }
                    try{Thread.sleep(150);}catch(InterruptedException end){return;}
                }
            },"analyst-cancel");
            watcher.setDaemon(true);watcher.start();
            try{
                if(cancel.get())throw new InterruptedException();
                String answer=conversation.sendMessage(prompt,Collections.emptyMap()).toString();
                if(cancel.get())throw new InterruptedException();
                return AnalystFinalAnswer.clean(answer);
            }catch(RuntimeException error){
                if(cancel.get())throw new InterruptedException();
                throw error;
            }finally{
                finished.set(true);watcher.interrupt();
                boolean interrupted=false;
                for(;;)try{watcher.join();break;}catch(InterruptedException e){interrupted=true;}
                if(interrupted)Thread.currentThread().interrupt();
            }
        }
    }
    // A conservative short-prompt tier: smaller KV cache for genuinely short requests.
    // Large evidence prompts and full scans keep the existing 4096-token context.
    static int contextTokens(String prompt,int outputLimit){
        return prompt!=null&&prompt.length()+outputLimit<=1500?2048:4096;
    }
    private static String single(String path,String prompt,int outputLimit,AtomicBoolean cancel)throws InterruptedException{
        if(cancel.get())throw new InterruptedException();
        String name=new java.io.File(path).getName();
        if(name.equals("analyst-qwen06q4.litertlm")){
            String completePrompt=AnalystShortPrompt.prepare(prompt);
            try(Engine engine=open(path,1280)){return infer(engine,completePrompt,AnalystShortPrompt.OUTPUT_LIMIT,cancel,true);}
        }
        if(name.equals("analyst-qwen25unc15.litertlm")){
            if(prompt.length()>1500)throw new IllegalArgumentException("Candidate requires short prompt");
            try(Engine engine=open(path,1280)){return infer(engine,prompt,256,cancel,true);}
        }
        if(name.equals("analyst-qwen2515.litertlm")){
            try(Engine engine=open(path,4096)){return infer(engine,prompt,512,cancel,true);}
        }
        boolean compactLegacy=name.equals("analyst-qwen06.litertlm");
        int budget=compactLegacy?Math.max(768,outputLimit):outputLimit;
        try(Engine engine=open(path,compactLegacy?4096:contextTokens(prompt,budget))){return infer(engine,prompt,budget,cancel,compactLegacy);}
    }
    public static FullChatModelScan.Report generateFull(String path,String dbPath,String question,
            java.util.concurrent.atomic.AtomicBoolean cancel,FullChatModelScan.Progress progress)throws Exception{
        java.io.File db=new java.io.File(dbPath);
        // Hybrid recurrent state can leak across fresh Conversation objects on one Engine
        // in upstream 0.18.x (#3165). Recreate the engine for unknown/hybrid weights.
        // This trades full-scan speed for independent prompts; do not hold two engines.
        if(!AnalystModelCatalog.canReuseEngine(path))
            return FullChatModelScan.read(db,question,cancel,prompt->single(path,prompt,512,cancel),progress);
        try(Engine engine=open(path,4096)){
            return FullChatModelScan.read(db,question,cancel,prompt->infer(engine,prompt,512,cancel,false),progress);
        }
    }
    /** Independent native calls; never synthesize a missing answer or load two engines. */
    public static String[] generateParts(String path,String[] prompts,AtomicBoolean cancel)throws InterruptedException{
        if(prompts==null||prompts.length<1||prompts.length>3)throw new IllegalArgumentException("Question plan size");
        String[] answers=new String[prompts.length];
        for(int i=0;i<prompts.length;i++){
            if(cancel.get())throw new InterruptedException();
            answers[i]=single(path,prompts[i],512,cancel);
        }
        return answers;
    }
    public static String generate(String path,String prompt)throws InterruptedException{
        return generate(path,prompt,new AtomicBoolean());
    }
    public static String generate(String path,String prompt,AtomicBoolean cancel)throws InterruptedException{
        return single(path,prompt,512,cancel);
    }
}
