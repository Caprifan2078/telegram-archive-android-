package org.openarchive.telegram;

import android.content.ContentResolver;
import android.database.sqlite.SQLiteDatabase;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Parse each HTML part once; create indexed media references without copying any media bytes. */
class TelegramImporter {
    interface Progress { void post(String text); }
    static final class Media {
        final String path, kind;
        Media(String p,String k){path=p;kind=k;}
    }
    static int importFiles(ContentResolver resolver,DocumentFolder folder,MessageDb helper,Progress progress) throws Exception {
        List<DocumentFolder.Entry> parts=folder.htmlParts();
        if(parts.isEmpty())throw new IllegalArgumentException("Не найдены messages.html. Выбери папку с распакованным экспортом.");
        SQLiteDatabase d=helper.getWritableDatabase();helper.clearStage(d);
        int saved=0,failures=0,mediaCount=0;
        String lastAuthor="";
        for(int f=0;f<parts.size();f++){
            DocumentFolder.Entry entry=parts.get(f);
            progress.post("Импорт "+(f+1)+" из "+parts.size()+": "+entry.name+" · "+saved+" сообщений");
            try(InputStream stream=resolver.openInputStream(folder.uriFor(entry.id))){
                if(stream==null)throw new IllegalStateException("Файл недоступен");
                String authorCandidate=lastAuthor;int written=0,medias=0;
                d.beginTransaction();
                try(TelegramHtmlStream messages=new TelegramHtmlStream(stream)){
                    Element msg;boolean found=false;
                    while((msg=messages.next())!=null){
                        found=true;
                        Element from=msg.selectFirst(".body .from_name");
                        Element date=msg.selectFirst(".body .date.details, .date.details");
                        Element content=msg.selectFirst(".body .text");
                        boolean service=msg.hasClass("service");
                        authorCandidate=TelegramAuthorPolicy.nextAuthor(authorCandidate,from==null?null:from.text(),service);
                        String when=date==null?"":date.hasAttr("title")?date.attr("title"):date.text();
                        String body=content==null?"":content.wholeText().trim();
                        if(body.isEmpty()&&content!=null)body=content.text();
                        List<Media> media=findMedia(msg);
                        if(msg.hasClass("service")&&body.isEmpty()){
                            Element event=msg.selectFirst(".body .details, .body");
                            if(event!=null)body=event.text();
                        }
                        if(!body.isEmpty()||!media.isEmpty()){
                            Media first=media.isEmpty()?null:media.get(0);
                            long messageId=helper.stage(d,TelegramAuthorPolicy.stageAuthor(authorCandidate,service),when,body,first==null?null:first.path,first==null?"":first.kind);
                            long telegramId=TelegramReplyPolicy.sourceMessageId(msg.id());
                            if(telegramId>0)helper.stageTelegramId(d,messageId,telegramId);
                            Element replyAnchor=msg.selectFirst(".reply_to a[href], .reply_to_details a[href]");
                            if(replyAnchor!=null){
                                long target=TelegramReplyPolicy.replyTargetFromHref(replyAnchor.attr("href"));
                                if(target>0)helper.stageReply(d,messageId,target);
                            }
                            for(Media a:media){helper.stageAttachment(d,messageId,a.path,a.kind);medias++;}
                            written++;
                        }
                    }
                    if(!found)throw new IllegalArgumentException("В HTML не найдено сообщений");
                    d.setTransactionSuccessful();
                }finally{d.endTransaction();}
                lastAuthor=authorCandidate;saved+=written;mediaCount+=medias;
            }catch(Exception ex){
                failures++;progress.post("Не прочитан "+entry.name+": "+ex.getMessage());
            }
        }
        if(saved==0)throw new IllegalStateException("Нет импортированных сообщений. Прежняя база сохранена.");
        if(failures>0)throw new IllegalStateException("Не удалось прочитать "+failures+" HTML-частей. Прошлая база сохранена — проверь файлы.");
        progress.post("Создаю поисковый индекс и каталог медиа…");
        helper.publishStaged(d);
        progress.post("Готово: "+saved+" сообщений · "+mediaCount+" вложений"+(failures>0?" · файлов с ошибками: "+failures:""));
        return saved;
    }
    /** Index all original media links, excluding thumbnail/avatar-only HTML decorations. */
    static List<Media> findMedia(Element msg){
        LinkedHashMap<String,Media> found=new LinkedHashMap<>();
        Elements files=msg.select(".body a[href],.body video[src],.body audio[src],.body source[src]");
        for(Element el:files){
            String path=el.hasAttr("href")?el.attr("href"):el.attr("src");
            if(!supported(path)||isPreview(path))continue;
            found.putIfAbsent(path,new Media(path,classify(path,el)));
        }
        // Some exports embed stickers as a direct src, without a downloadable anchor.
        // Include native TGS/WebM stickers; avoid indexing photo previews twice.
        for(Element e:msg.select(".body .media_wrap img[src],.body .photo_wrap img[src],.body .sticker_wrap img[src]")){
            String path=e.attr("src");
            String low=path.toLowerCase(Locale.ROOT).split("[?#]",2)[0];
            boolean sticker=e.closest(".sticker_wrap")!=null;
            boolean nativeSticker=sticker&&(low.endsWith(".tgs")||low.endsWith(".webm"));
            if(!supported(path)||isPreview(path)||(!isPicture(path)&&!nativeSticker))continue;
            // A linked image is normally a preview of the href asset already collected.
            // Do not reject other independent, unlinked photos just because a media item exists.
            Element anchor=e.closest("a[href]");
            if(anchor!=null&&supported(anchor.attr("href"))&&!nativeSticker)continue;
            found.putIfAbsent(path,new Media(path,classify(path,e)));
        }
        return new ArrayList<>(found.values());
    }
    private static boolean isPicture(String p){return p.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg|webp|gif|heic|bmp)(\\?.*)?$");}
    static String kindFromExtension(String path){
        String s=path.toLowerCase(Locale.ROOT).split("[?#]",2)[0];
        if(s.matches(".*\\.(mp4|mov|m4v|webm|mkv)$"))return "video";
        if(s.matches(".*\\.(ogg|opus|mp3|m4a|wav|flac|aac|wma)$"))return "audio";
        if(s.matches(".*\\.(jpg|jpeg|png|webp|heic|bmp|gif|apng)$"))return "image";
        return "file";
    }
    static String classify(String path,Element node){
        String low=path.toLowerCase(Locale.ROOT);
        String base=kindFromExtension(path);
        boolean voice=low.contains("voice_messages")||low.contains("voice_message")||low.contains("audio_message")||low.contains("voice_note")
            || ((low.matches(".*\\.(ogg|opus)(?:[?#].*)?$")||low.endsWith(".ogg")||low.endsWith(".opus"))
                &&!low.contains("music")&&!low.contains("songs/"));
        boolean round=low.contains("round_video")||low.contains("video_message")||low.contains("video_note");
        boolean sticker=low.startsWith("stickers/")||low.contains("/stickers/")||low.matches(".*\\.tgs(?:[?#].*)?$");
        boolean gif=low.matches(".*\\.gif(?:[?#].*)?$")||low.startsWith("animations/")||low.startsWith("gifs/")||low.startsWith("animated/")||low.contains("/animations/")||low.contains("/gifs/")||low.contains("/animated/")||low.contains("animation")&&low.endsWith(".mp4");
        for(Element p=node;p!=null;p=p.parent()){
            String cls=p.className().toLowerCase(Locale.ROOT);
            if(cls.contains("voice_message")||cls.contains("voice_note")||cls.contains("audio_message"))voice=true;
            if(cls.contains("round_video")||cls.contains("video_message")||cls.contains("video_note"))round=true;
            if(cls.contains("sticker"))sticker=true;
            if(cls.contains("gif")||cls.contains("animation")||cls.contains("animated"))gif=true;
            if(p.hasClass("message"))break;
        }
        // Semantic media types take precedence over their physical container/extension.
        if(sticker)return "sticker";
        if("video".equals(base))return round?"round":gif?"gif":"video";
        if("audio".equals(base))return voice?"voice":"audio";
        if("image".equals(base)&&gif)return "gif";
        return base;
    }
    static boolean supported(String path){
        if(path==null||path.isEmpty()||path.startsWith("/")||path.contains(":")||path.startsWith("#"))return false;
        String s=path.toLowerCase(Locale.ROOT).split("[?#]",2)[0];
        return s.matches(".*\\.(mp4|mov|m4v|webm|mkv|ogg|opus|mp3|m4a|wav|flac|aac|wma|jpg|jpeg|png|gif|webp|heic|bmp|apng|tgs|pdf|zip|rar|7z|doc|docx|xls|xlsx|ppt|pptx|txt|csv|json|xml|apk|epub|db|sqlite|psd|ai|exe|torrent|rtf|odt|ods|odp)$");
    }
    private static boolean isPreview(String path){
        String s=path.toLowerCase(Locale.ROOT).split("[?#]",2)[0];
        return s.contains("_thumb.")||s.contains("_thumb_")||s.contains("thumbnail.");
    }
}
