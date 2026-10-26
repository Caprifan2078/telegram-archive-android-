package org.openarchive.telegram;

/** Stable calendar-day matching for locally exported messages. Never guesses time zones. */
final class OnThisDay {
    static final class Entry {
        final long id;
        final String author,date,preview;
        Entry(long id,String author,String date,String body){
            this.id=id;
            this.author=author==null?"":author;
            this.date=date==null?"":date;
            String cleaned=(body==null?"":body).replace('\r',' ').replace('\n',' ').trim();
            // Bound UI text without splitting surrogate pairs.
            int count=cleaned.codePointCount(0,cleaned.length());
            this.preview=count>120?cleaned.substring(0,cleaned.offsetByCodePoints(0,120))+"…":cleaned;
        }
    }
    static boolean matches(String date,int month,int day){
        if(month<1||month>12||day<1||day>31)return false;
        ChatTrends.Moment m=ChatTrends.parse(date);
        return m!=null && m.day.getMonthValue()==month && m.day.getDayOfMonth()==day;
    }
    private OnThisDay(){}
}
