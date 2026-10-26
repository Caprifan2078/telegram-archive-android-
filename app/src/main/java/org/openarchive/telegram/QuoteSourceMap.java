package org.openarchive.telegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only source map for a user-saved quotation.
 * Adjacent entries are neighbors in stored message-ID order, NOT confirmed replies.
 * All data stays on-device and the original messages are never modified. */
final class QuoteSourceMap {
    static final int RADIUS=2;
    static final int MAX_PREVIEW_CODEPOINTS=140;
    static final class Entry {
        final long id;
        final String author,date,body;
        Entry(long id,String author,String date,String body){
            if(id<=0)throw new IllegalArgumentException("Invalid message ID");
            this.id=id;
            this.author=author==null?"":author;
            this.date=date==null?"":date;
            this.body=body==null?"":body;
        }
    }
    static final class Context {
        final long sourceId;
        final boolean found;
        final List<Entry> messages;
        final int sourceIndex;
        Context(long sourceId,Entry source,List<Entry> beforeAscending,List<Entry> afterAscending){
            if(sourceId<=0)throw new IllegalArgumentException("Invalid quote source ID");
            this.sourceId=sourceId;
            this.found=source!=null;
            ArrayList<Entry> out=new ArrayList<>();
            if(source!=null){
                if(source.id!=sourceId)throw new IllegalArgumentException("Source ID mismatch");
                if(beforeAscending!=null){
                    if(beforeAscending.size()>RADIUS)throw new IllegalArgumentException("Too many preceding messages");
                    long previous=Long.MIN_VALUE;
                    for(Entry item:beforeAscending){
                        if(item==null||item.id>=sourceId||item.id<=previous)throw new IllegalArgumentException("Invalid preceding message order");
                        out.add(item);previous=item.id;
                    }
                }
                sourceIndex=out.size();out.add(source);
                if(afterAscending!=null){
                    if(afterAscending.size()>RADIUS)throw new IllegalArgumentException("Too many following messages");
                    long previous=sourceId;
                    for(Entry item:afterAscending){
                        if(item==null||item.id<=previous)throw new IllegalArgumentException("Invalid following message order");
                        out.add(item);previous=item.id;
                    }
                }
            }else sourceIndex=-1;
            messages=Collections.unmodifiableList(out);
        }
        String positionLabel(int index){
            if(index<0||index>=messages.size())throw new IndexOutOfBoundsException();
            if(index==sourceIndex)return "● Исходное сообщение";
            return index<sourceIndex?"↑ Ранее в архиве":"↓ Позже в архиве";
        }
    }
    static String preview(String value){
        if(value==null||value.trim().isEmpty())return "(сообщение без текста)";
        String clean=value.replaceAll("\\s+"," ").trim();
        int count=clean.codePointCount(0,clean.length());
        if(count<=MAX_PREVIEW_CODEPOINTS)return clean;
        int end=clean.offsetByCodePoints(0,MAX_PREVIEW_CODEPOINTS);
        return clean.substring(0,end)+"…";
    }
    private QuoteSourceMap(){}
}
