package org.openarchive.telegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A bounded question plan for small models. Do not guess where an ambiguous
 * sentence should be split. All parts must be independently phrased questions. */
final class AnalystQuestionParts {
    static boolean lightweight(String modelKey){
        return "qwen06".equals(modelKey)||"qwen06q4".equals(modelKey);
    }
    static List<String> split(String question){
        if(question==null)return Collections.emptyList();
        String q=question.trim();
        if(q.isEmpty()||q.length()>360||q.contains("\"")||q.contains("«")||q.contains("»"))
            return Collections.singletonList(q);
        String[] parts=q.split("(?<=\\?)\\s+");
        if(parts.length<2||parts.length>3)return Collections.singletonList(q);
        List<String> out=new ArrayList<>();
        for(String p:parts){
            String part=p.trim();
            if(part.length()<7||!part.endsWith("?")||part.indexOf('?')!=part.length()-1||
                !part.matches("(?iu)^(кто|что|где|когда|почему|зачем|как|сколько|какой|какая|какие|какое|в\\s+какое|в\\s+каком|во\\s+сколько)\\s+.+\\?$"))
                return Collections.singletonList(q);
            out.add(part);
        }
        return out;
    }
    static boolean hasEvidence(String answer,String prompt){
        return answer!=null&&!answer.trim().isEmpty()&&!FullChatModelScan.ids(answer).isEmpty()&&
            DeepChatContext.unknownCitations(answer,prompt).isEmpty();
    }
    private AnalystQuestionParts(){}
}
