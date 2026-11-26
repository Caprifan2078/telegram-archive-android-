package org.openarchive.telegram;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.SamplerConfig;
import com.google.ai.edge.litertlm.LogSeverity;
import java.util.Collections;

/** Opt-in local model build; LiteRT-LM 0.18.0. CPU-first, one engine at a time. */
public final class LocalLlmRuntime {
    private static Engine open(String path){
        Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);
        Engine engine=new Engine(new EngineConfig(path,new Backend.CPU(2,null),null,null,4096,null,":nocache",null));
        engine.initialize();
        return engine;
    }
    private static String infer(Engine engine,String prompt,int outputLimit){
        ConversationConfig config=new ConversationConfig(null,Collections.emptyList(),Collections.emptyList(),
            new SamplerConfig(20,0.85,0.2,0),false,null,Collections.emptyMap(),null,false,outputLimit,null,false,false,null);
        try(Conversation conversation=engine.createConversation(config)){
            return conversation.sendMessage(prompt,Collections.emptyMap()).toString();
        }
    }
    private static String single(String path,String prompt,int outputLimit){
        try(Engine engine=open(path)){return infer(engine,prompt,outputLimit);}
    }
    public static FullChatModelScan.Report generateFull(String path,String dbPath,String question,
            java.util.concurrent.atomic.AtomicBoolean cancel,FullChatModelScan.Progress progress)throws Exception{
        java.io.File db=new java.io.File(dbPath);
        // Hybrid recurrent state can leak across fresh Conversation objects on one Engine
        // in upstream 0.18.x (#3165). Recreate the engine for unknown/hybrid weights.
        // This trades full-scan speed for independent prompts; do not hold two engines.
        if(!AnalystModelCatalog.canReuseEngine(path))
            return FullChatModelScan.read(db,question,cancel,prompt->single(path,prompt,512),progress);
        try(Engine engine=open(path)){
            return FullChatModelScan.read(db,question,cancel,prompt->infer(engine,prompt,512),progress);
        }
    }
    public static String generate(String path,String prompt){
        return single(path,prompt,768);
    }
}
