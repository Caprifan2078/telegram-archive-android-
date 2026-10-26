package org.openarchive.telegram;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Explicit opt-in, offline substring search for recurring personal phrases.
 * Neither relationships nor emotions are inferred from a phrase match. */
final class PhraseEcho {
    static final int MAX_PHRASE=100, MAX_RESULTS=100, MAX_SCANNED=300000;
    static final class Hit {
        final long messageId;
        final String author,date,preview;
        Hit(long id,String author,String date,String preview){
            this.messageId=id;this.author=author==null?"":author;
            this.date=date==null?"":date;this.preview=preview==null?"":preview;
        }
    }
    static final class Report {
        final List<Hit> examples;
        final int scanned,matchingMessages,occurrences;
        final boolean partial;
        Report(List<Hit> entries,int scan,int matches,int total,boolean partial){
            this.examples=Collections.unmodifiableList(new ArrayList<>(entries));
            this.scanned=scan;this.matchingMessages=matches;this.occurrences=total;
            this.partial=partial;
        }
    }
    static String canonical(String s){
        return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKC)
            .replace('\u00a0',' ').toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
    }
    static String requirePhrase(String s){
        String p=canonical(s);
        if(p.length()<2||p.length()>MAX_PHRASE)
            throw new IllegalArgumentException("Фраза: 2–100 символов");
        return p;
    }
    static int count(String normalizedText,String phrase){
        if(phrase.isEmpty())return 0;
        int count=0,offset=0;
        while((offset=normalizedText.indexOf(phrase,offset))>=0){
            count++;offset+=phrase.length();
        }
        return count;
    }
    static String preview(String text,String phrase){
        String original=text==null?"":text;
        String normal=canonical(original);
        int position=normal.indexOf(phrase);
        // Normalization may change string lengths: take safe approximate context only.
        int start=Math.max(0,position-42),end=Math.min(original.length(),Math.max(0,position)+phrase.length()+80);
        if(start>original.length())start=original.length();
        if(end<start)end=start;
        return (start>0?"…":"")+original.substring(start,end).replaceAll("\\s+"," ")+(end<original.length()?"…":"");
    }
    private PhraseEcho(){}
}
