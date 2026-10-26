package org.openarchive.telegram;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict offline parsing of internal Telegram HTML message anchors. Never opens href URLs. */
final class TelegramReplyPolicy {
    private static final Pattern MESSAGE_ID=Pattern.compile("^message([1-9][0-9]{0,17})$");
    private static final Pattern ANCHOR_ID=Pattern.compile("^(?:go_to_)?message([1-9][0-9]{0,17})$");
    private static final Pattern SAFE_PART=Pattern.compile("^(?:messages(?:[1-9][0-9]*)?\\.html)?$",Pattern.CASE_INSENSITIVE);
    private TelegramReplyPolicy(){}
    static long sourceMessageId(String id){
        if(id==null)return -1;
        Matcher m=MESSAGE_ID.matcher(id.trim());
        if(!m.matches())return -1;
        try {return Long.parseLong(m.group(1));}catch(NumberFormatException e){return -1;}
    }
    static long replyTargetFromHref(String href){
        if(href==null||href.length()>170)return -1;
        int hash=href.indexOf('#');
        if(hash<0||href.indexOf('#',hash+1)>=0)return -1;
        String file=href.substring(0,hash);
        if(!SAFE_PART.matcher(file).matches())return -1;
        Matcher m=ANCHOR_ID.matcher(href.substring(hash+1));
        if(!m.matches())return -1;
        try{return Long.parseLong(m.group(1));}catch(NumberFormatException e){return -1;}
    }
}
