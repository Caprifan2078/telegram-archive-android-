package org.openarchive.telegram;

/** Emits only synthetic FTS4 queries from production search planning code. */
public final class YoSearchHostCheck {
    public static void main(String[] args) {
        for (String term : new String[]{"ёлка", "елка", "ёжик", "ежик", "всё", "все"}) {
            OfflineSynonymSearch.Plan plan=OfflineSynonymSearch.make(term,true);
            if(!plan.fts.contains("body:")) throw new AssertionError("No FTS expression: "+term);
            System.out.println(term+"\t"+plan.fts);
        }
        // A typo suggestion must keep the real spelling returned by FTS4aux.
        java.util.List<String> suggestions=FuzzyWordPolicy.suggest("ёлочкк",
            java.util.Arrays.asList("ёлочка","молоко"));
        if(!suggestions.equals(java.util.Collections.singletonList("ёлочка")))
            throw new AssertionError("Cyrillic ё was lost in a fuzzy suggestion");
        java.util.Map<String,java.util.List<String>> typos=new java.util.LinkedHashMap<>();
        typos.put("ёлочкк",suggestions);
        OfflineSynonymSearch.Plan corrected=OfflineSynonymSearch.make("ёлочкк",true,false,typos);
        if(!corrected.fts.contains("body:ёлочка"))
            throw new AssertionError("Fuzzy plan lost source index spelling");
        System.out.println("ё-опечатка\t"+corrected.fts);
        OfflineSynonymSearch.Plan optOut=OfflineSynonymSearch.make("ёлка",false);
        if(!"body:ёлка*".equals(optOut.fts)) throw new AssertionError("Exact-mode ё changed");
    }
}
