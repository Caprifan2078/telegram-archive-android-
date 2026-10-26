package org.openarchive.telegram;
import java.util.Locale;
/** Side-effect-free font validation. Never trust a font merely by extension or MIME type. */
final class FontPolicy {
    static final int MAX_BYTES=5*1024*1024;
    static final String[] KEYS={"system","sans","serif","mono","condensed","casual","light","medium","custom"};
    private FontPolicy(){}
    static boolean known(String key){
        for(String allowed:KEYS)if(allowed.equals(key))return true;
        return false;
    }
    static boolean acceptedName(String name){
        if(name==null || name.length()>250)return false;
        String n=name.toLowerCase(Locale.ROOT);
        return n.endsWith(".ttf")||n.endsWith(".otf");
    }
    static boolean validHeader(byte[] bytes,int count){
        if(bytes==null||count<4)return false;
        int a=bytes[0]&255,b=bytes[1]&255,c=bytes[2]&255,d=bytes[3]&255;
        return (a==0&&b==1&&c==0&&d==0) // TrueType
            ||(a=='O'&&b=='T'&&c=='T'&&d=='O') // OpenType/CFF
            ||(a=='t'&&b=='r'&&c=='u'&&d=='e'); // Apple TrueType
    }
    static boolean withinLimit(long size){return size>4&&size<=MAX_BYTES;}
}
