package org.openarchive.telegram;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Match offsets are always UTF-16 positions in the original, unchanged text. */
final class SearchHighlightPolicy {
    private SearchHighlightPolicy() {}
    static List<int[]> ranges(String body,String query,boolean deep) {
        List<int[]> found=new ArrayList<>();
        if(body==null||query==null||query.trim().isEmpty())return found;
        String[] needles=deep?new String[]{query}:query.split("[^\\p{L}\\p{N}]+");
        for(String needle:needles){
            if(needle.isEmpty())continue;
            Matcher matcher=Pattern.compile(Pattern.quote(needle),
                Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(body);
            int count=0;
            while(count<30&&matcher.find()){
                found.add(new int[]{matcher.start(),matcher.end()});count++;
            }
        }
        return found;
    }
}
