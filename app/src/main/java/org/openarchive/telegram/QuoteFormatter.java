package org.openarchive.telegram;

/** Deterministic local quotes. Never sends data or changes imported messages. */
final class QuoteFormatter {
    private QuoteFormatter(){}
    static String excerpt(String original,int start,int end){
        if(original==null)return "";
        if(start<0||end<0||start>=end||end>original.length())return original.trim();
        return original.substring(start,end).trim();
    }
    static String format(String text,String author,String date,String chat){
        if(text==null||text.trim().isEmpty())throw new IllegalArgumentException("Пустая цитата");
        String content=text.replace("\r\n","\n").replace('\r','\n').trim();
        if(content.length()>4000)content=content.substring(0,4000)+"…";
        String who=cleanMeta(author,100,"Неизвестный участник");
        String when=cleanMeta(date,70,"Дата неизвестна");
        String archive=cleanMeta(chat,100,"Архив");
        // Preserve line breaks and visually distinguish literal text from provenance.
        StringBuilder result=new StringBuilder();
        for(String line:content.split("\n",-1))result.append("│ ").append(line).append('\n');
        result.append("— ").append(who).append(" · ").append(when).append(" · ").append(archive);
        return result.toString();
    }
    private static String cleanMeta(String value,int max,String fallback){
        if(value==null||value.trim().isEmpty())return fallback;
        String result=value.trim().replace('\r',' ').replace('\n',' ');
        return result.length()>max?result.substring(0,max)+"…":result;
    }
}
