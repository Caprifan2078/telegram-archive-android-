package org.openarchive.telegram;

/** Some exported model templates still emit literal thinking markers. Do not
 * accept citations or facts from a discarded reasoning block as final answers. */
final class AnalystFinalAnswer {
    static String clean(String raw){
        if(raw==null)return "";
        String s=raw.trim();
        int start=s.indexOf("<think>");
        while(start>=0){
            int end=s.indexOf("</think>",start+7);
            if(end<0)return s.substring(0,start).trim();
            s=(s.substring(0,start)+s.substring(end+8)).trim();
            start=s.indexOf("<think>");
        }
        if(s.startsWith("</think>"))s=s.substring(8).trim();
        // Canonicalize explicit bare source references emitted by small models.
        // Never turn ordinary numbers or times into citations; unknown IDs remain unknown.
        return s.replaceAll("(?<![\\p{L}\\p{N}\\[＃])#([0-9]{1,18})(?![\\p{L}\\p{N}\\]])", "[#$1]");
    }
    private AnalystFinalAnswer(){}
}
