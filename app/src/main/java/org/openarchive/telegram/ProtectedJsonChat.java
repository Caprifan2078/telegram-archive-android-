package org.openarchive.telegram;

import android.util.JsonReader;
import android.util.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads one Telegram result.json chat, not a multi-chat account export. */
final class ProtectedJsonChat {
    static final class Message {
        final String display;
        final List<TelegramImporter.Media> media;
        Message(String display,List<TelegramImporter.Media> media){this.display=display;this.media=media;}
    }
    static List<Message> parse(InputStream input)throws IOException{
        ArrayList<Message> messages=new ArrayList<>();long chars=0;boolean found=false;
        try(JsonReader r=new JsonReader(new InputStreamReader(input,StandardCharsets.UTF_8))){
            r.beginObject();
            while(r.hasNext()){
                String name=r.nextName();
                if(!"messages".equals(name)){r.skipValue();continue;}
                if(found)throw new IOException("Duplicate messages array");
                found=true;r.beginArray();
                while(r.hasNext()){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                    Message m=message(r);
                    if(m==null)continue;
                    chars+=m.display.length();
                    for(TelegramImporter.Media media:m.media)chars+=media.path.length();
                    if(messages.size()>=100000||chars>16L*1024*1024)
                        throw new IOException("Чат превышает лимит защищённого режима");
                    messages.add(m);
                }
                r.endArray();
            }
            r.endObject();
            if(r.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Invalid trailing JSON");
        }
        if(!found||messages.isEmpty())throw new IOException("Не найден одиночный чат Telegram в JSON");
        return messages;
    }
    private static Message message(JsonReader r)throws IOException{
        if(r.peek()!=JsonToken.BEGIN_OBJECT){r.skipValue();return null;}
        String from="",actor="",date="",body="",file="",photo="",kind="",action="";
        r.beginObject();
        while(r.hasNext()){
            String name=r.nextName();
            switch(name){
                case "from":from=scalar(r);break;
                case "actor":actor=scalar(r);break;
                case "date":date=scalar(r);break;
                case "text":body=text(r);break;
                case "file":file=scalar(r);break;
                case "photo":photo=scalar(r);break;
                case "media_type":kind=scalar(r);break;
                case "action":action=scalar(r);break;
                default:r.skipValue();
            }
        }
        r.endObject();
        List<TelegramImporter.Media> media=new ArrayList<>();
        if(TelegramImporter.supported(photo))media.add(new TelegramImporter.Media(photo,"image"));
        if(TelegramImporter.supported(file)&&!file.equals(photo)){
            String k=TelegramImporter.kindFromExtension(file);
            switch(kind){
                case "voice_message":k="voice";break;
                case "video_message":k="round";break;
                case "video_file":k="video";break;
                case "audio_file":k="audio";break;
                case "animation":k="gif";break;
                case "sticker":k="sticker";break;
            }
            media.add(new TelegramImporter.Media(file,k));
        }
        if(body.isEmpty()&&media.isEmpty()&&action.isEmpty())return null;
        String author=from.isEmpty()?actor:from;
        String value=(author.isEmpty()?"":author+"\n")+(date.isEmpty()?"":date+"\n")
            +(body.isEmpty()?action:body);
        if(!media.isEmpty())value+="\nВложения: "+media.size()+" · нажми для просмотра";
        return new Message(value,media);
    }
    private static String scalar(JsonReader r)throws IOException{
        JsonToken token=r.peek();
        if(token==JsonToken.STRING||token==JsonToken.NUMBER)return r.nextString();
        r.skipValue();return "";
    }
    private static String text(JsonReader r)throws IOException{
        if(r.peek()!=JsonToken.BEGIN_ARRAY)return scalar(r);
        StringBuilder out=new StringBuilder();r.beginArray();
        while(r.hasNext()){
            if(r.peek()==JsonToken.BEGIN_OBJECT){
                r.beginObject();while(r.hasNext()){
                    if("text".equals(r.nextName()))out.append(scalar(r));else r.skipValue();
                }r.endObject();
            }else out.append(scalar(r));
            if(out.length()>2*1024*1024)throw new IOException("Слишком длинное сообщение");
        }
        r.endArray();return out.toString();
    }
}
