package org.openarchive.telegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Pure, bounded, optional spelling suggestions. No network, ML, or full-message scan. */
final class FuzzyWordPolicy {
    static final int MAX_CANDIDATES = 650;
    static final int MAX_EXPANSIONS = 8;
    static final int MIN_LENGTH = 5;
    private FuzzyWordPolicy() {}

    static String normalized(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    /** One insertion, removal, substitution, or adjacent transpose; matches only corpus terms. */
    static boolean near(String input, String candidate) {
        String a=normalized(input), b=normalized(candidate);
        int n=a.length(), m=b.length();
        if (n<4 || m<4 || Math.max(n,m)<MIN_LENGTH || Math.abs(n-m)>1 || n>24 || m>24) return false;
        // Two-letter anchor reduces false positives and constrains the index vocabulary lookup.
        if (!a.substring(0,2).equals(b.substring(0,2)) || a.equals(b)) return false;
        if (!a.matches("[\\p{L}]+") || !b.matches("[\\p{L}]+")) return false;
        int i=0,j=0,edits=0;
        while(i<n && j<m) {
            if(a.charAt(i)==b.charAt(j)){i++;j++;continue;}
            if(++edits>1)return false;
            if(n==m){
                if(i+1<n && j+1<m && a.charAt(i)==b.charAt(j+1) && a.charAt(i+1)==b.charAt(j)) {
                    i+=2;j+=2;continue;
                }
                i++;j++;
            } else if(n>m)i++;
            else j++;
        }
        return edits+(n-i)+(m-j)<=1;
    }

    static List<String> suggest(String query, Iterable<String> fromIndex) {
        ArrayList<String> results=new ArrayList<>();
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        int checked=0;
        for(String w:fromIndex){
            if(++checked>MAX_CANDIDATES)break;
            String word=normalized(w);
            if(near(query,word) && seen.add(word)) {
                results.add(word);
                if(results.size()==MAX_EXPANSIONS)break;
            }
        }
        return Collections.unmodifiableList(results);
    }

    /** Exclusive Unicode upper bound for prefix search without a large string LIKE scan. */
    static String prefixUpperBound(String prefix) {
        if(prefix==null || prefix.isEmpty())throw new IllegalArgumentException("empty prefix");
        int end=prefix.length()-1;
        return prefix.substring(0,end)+(char)(prefix.charAt(end)+1);
    }
}
