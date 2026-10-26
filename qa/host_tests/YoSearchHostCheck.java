package org.openarchive.telegram;

/** Emits only synthetic FTS4 queries from production search planning code. */
public final class YoSearchHostCheck {
    public static void main(String[] args) {
        for (String term : new String[]{"ёлка", "елка", "ёжик", "ежик", "всё", "все"}) {
            OfflineSynonymSearch.Plan plan=OfflineSynonymSearch.make(term,true);
            if(!plan.fts.contains("body:")) throw new AssertionError("No FTS expression: "+term);
            System.out.println(term+"\t"+plan.fts);
        }
        OfflineSynonymSearch.Plan optOut=OfflineSynonymSearch.make("ёлка",false);
        if(!"body:ёлка*".equals(optOut.fts)) throw new AssertionError("Exact-mode ё changed");
    }
}
