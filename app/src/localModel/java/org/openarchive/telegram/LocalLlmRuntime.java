package org.openarchive.telegram;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.SamplerConfig;
import com.google.ai.edge.litertlm.LogSeverity;
import java.util.Collections;

/** Compiled only in explicit local-model builds. API pinned to LiteRT-LM 0.10.2. */
public final class LocalLlmRuntime {
    private static Engine open(String path){
        Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);
        Engine engine=new Engine(new EngineConfig(path,new Backend.CPU(2),null,null,4096,null,":nocache"));
        engine.initialize();return engine;
    }
    private static String infer(Engine engine,String prompt){
        ConversationConfig config=new ConversationConfig(null,Collections.emptyList(),Collections.emptyList(),
            new SamplerConfig(20,0.85,0.2,0),false,null,Collections.emptyMap());
        try(Conversation conversation=engine.createConversation(config)){
            return conversation.sendMessage(prompt,Collections.emptyMap()).toString();
        }
    }
    public static FullChatModelScan.Report generateFull(String path,String dbPath,String question,
            java.util.concurrent.atomic.AtomicBoolean cancel,FullChatModelScan.Progress progress)throws Exception{
        try(Engine engine=open(path)){
            return FullChatModelScan.read(new java.io.File(dbPath),question,cancel,prompt->infer(engine,prompt),progress);
        }
    }
    public static String generate(String path,String prompt) {
        Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);
        Engine engine=new Engine(new EngineConfig(path,new Backend.CPU(2),null,null,4096,null,":nocache"));
        try{
            engine.initialize();
            ConversationConfig config=new ConversationConfig(null,Collections.emptyList(),Collections.emptyList(),
                new SamplerConfig(20,0.85,0.2,0),false,null,Collections.emptyMap());
            try(Conversation conversation=engine.createConversation(config)){
                return conversation.sendMessage(prompt,Collections.emptyMap()).toString();
            }
        }finally{if(engine.isInitialized())engine.close();}
    }
}
