package org.openarchive.telegram;

import java.util.Locale;

/** One document picker, deterministic routing; never import random files as Telegram chats. */
final class TelegramImportSource {
    enum Kind { ZIP, SEVEN_Z, RAR, HTML, PREVIEW }

    private TelegramImportSource(){}

    static Kind detect(String name, byte[] prefix){
        int n=prefix==null?0:prefix.length;
        // Prefer real archive signatures, including for files with missing/wrong extensions.
        if(n>=4 && prefix[0]=='P' && prefix[1]=='K' &&
            ((prefix[2]==3&&prefix[3]==4)||(prefix[2]==5&&prefix[3]==6)||(prefix[2]==7&&prefix[3]==8)))return Kind.ZIP;
        if(n>=6 && (prefix[0]&255)==0x37&&(prefix[1]&255)==0x7a&&(prefix[2]&255)==0xbc&&
            (prefix[3]&255)==0xaf&&(prefix[4]&255)==0x27&&(prefix[5]&255)==0x1c)return Kind.SEVEN_Z;
        if(n>=7 && prefix[0]=='R' && prefix[1]=='a' && prefix[2]=='r' && prefix[3]=='!' &&
            (prefix[4]&255)==0x1a&&(prefix[5]&255)==0x07)return Kind.RAR;
        String lower=name==null?"":name.toLowerCase(Locale.ROOT).trim();
        if(lower.endsWith(".zip"))return Kind.ZIP;
        if(lower.endsWith(".7z"))return Kind.SEVEN_Z;
        if(lower.endsWith(".rar"))return Kind.RAR;
        if(lower.endsWith(".html")||lower.endsWith(".htm"))return Kind.HTML;
        // Unrecognized Office, PDF, images etc. are opened for preview, never indexed as messages.
        return Kind.PREVIEW;
    }

    static String suggestedChatName(String file){
        if(file==null)return "Переписка";
        String result=file.replace('\\','/');result=result.substring(result.lastIndexOf('/')+1);
        result=result.replaceFirst("(?i)\\.(zip|7z|rar|html|htm)$","").trim();
        result=result.replaceAll("^[. _-]+|[. _-]+$","");
        if(result.matches("(?i)chat[ _-]?export[ _-]?\\d{4}[-_]\\d{2}[-_]\\d{2}") ||
           result.matches("(?i)messages\\d*"))return "Переписка";
        if(result.isEmpty())return "Переписка";
        return result.length()>80?result.substring(0,80):result;
    }
}
