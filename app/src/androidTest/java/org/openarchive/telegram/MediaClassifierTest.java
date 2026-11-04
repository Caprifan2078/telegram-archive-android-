package org.openarchive.telegram;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Tests app media classification used by the gallery, voice tab and video-circle player. */
@RunWith(AndroidJUnit4.class)
public class MediaClassifierTest {
    private Element element(String path,String cls){
        return Jsoup.parse("<div class='message'><div class='body'><div class='"+cls+"'><a href='"+path+"'>media</a></div></div></div>").selectFirst("a[href]");
    }
    @Test public void separateEveryMediaTypeWithoutDuplicates(){
        assertEquals("round",TelegramImporter.classify("round_video/message1.mp4",element("round_video/message1.mp4","round_video")));
        assertEquals("voice",TelegramImporter.classify("voice_messages/audio1.ogg",element("voice_messages/audio1.ogg","voice_message")));
        assertEquals("video",TelegramImporter.classify("video_files/video.mp4",element("video_files/video.mp4","video")));
        assertEquals("image",TelegramImporter.classify("photos/photo.jpg",element("photos/photo.jpg","photo")));
        assertEquals("gif",TelegramImporter.classify("animations/fun.mp4",element("animations/fun.mp4","animation")));
        assertEquals("sticker",TelegramImporter.classify("stickers/hi.webp",element("stickers/hi.webp","sticker")));
        assertEquals("audio",TelegramImporter.classify("audio/song.mp3",element("audio/song.mp3","music")));
        assertEquals("file",TelegramImporter.classify("files/test.pdf",element("files/test.pdf","document")));
    }
    @Test public void importsDirectPicturesWithFragmentsAndApng(){
        Element message=Jsoup.parse("<div class='message'><div class='body'>"+
            "<div class='photo_wrap'><img src='photos/one.jpg#view'></div>"+
            "<div class='photo_wrap'><img src='photos/two.apng?size=full'></div>"+
            "</div></div>").selectFirst(".message");
        java.util.List<TelegramImporter.Media> media=TelegramImporter.findMedia(message);
        assertEquals(2,media.size());
        assertEquals("photos/one.jpg#view",media.get(0).path);
        assertEquals("image",media.get(0).kind);
        assertEquals("photos/two.apng?size=full",media.get(1).path);
        assertEquals("image",media.get(1).kind);
    }
}
