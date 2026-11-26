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
    public static String generate(String path,String prompt) {
        Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);
        Engine engine=new Engine(new EngineConfig(path,new Backend.CPU(4),null,null,8192,null,":nocache"));
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
