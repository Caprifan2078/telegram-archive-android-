package org.openarchive.telegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded, read-only audit summary. A missing SAF resolution is NOT proof of data loss. */
final class ChatMediaHealth {
    static final int DEFAULT_SCAN_LIMIT=2000;
    static final int MAX_EXAMPLES=12;
    static final class Report {
        final int scanned,accessible,unresolved,totalDistinct;
        final boolean truncated;
        final List<String> unresolvedExamples;
        Report(int scanned,int accessible,int unresolved,int totalDistinct,List<String> examples){
            this.scanned=scanned;this.accessible=accessible;this.unresolved=unresolved;
            this.totalDistinct=totalDistinct;this.truncated=scanned<totalDistinct;
            this.unresolvedExamples=Collections.unmodifiableList(new ArrayList<>(examples));
        }
    }
    static final class Builder {
        private int scanned,accessible,unresolved;
        private final ArrayList<String> examples=new ArrayList<>();
        void record(String path,boolean resolved){
            scanned++;
            if(resolved)accessible++;
            else{
                unresolved++;
                if(examples.size()<MAX_EXAMPLES)
                    examples.add(path==null?"(пустой путь)":path.length()>140?path.substring(0,140)+"…":path);
            }
        }
        Report finish(int distinct){return new Report(scanned,accessible,unresolved,Math.max(distinct,scanned),examples);}
    }
    private ChatMediaHealth(){}
}
