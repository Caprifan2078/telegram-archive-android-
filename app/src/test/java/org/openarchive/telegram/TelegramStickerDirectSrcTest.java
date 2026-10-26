package org.openarchive.telegram;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Test;
import static org.junit.Assert.*;

public class TelegramStickerDirectSrcTest {
    private static Element message(String html) {
        return Jsoup.parse("<div class='message'><div class='body'>" + html + "</div></div>").selectFirst(".message");
    }
    @Test public void directTgsDetected() {
        assertEquals("sticker", TelegramImporter.findMedia(message("<div class='sticker_wrap'><img src='stickers/s.tgs'></div>")).get(0).kind);
    }
    @Test public void directWebmDetectedAsSticker() {
        assertEquals("sticker", TelegramImporter.findMedia(message("<div class='sticker_wrap'><img src='stickers/s.webm'></div>")).get(0).kind);
    }
    @Test public void nativeStickerSurvivesPreviewAnchor() {
        Element msg=message("<div class='sticker_wrap'><a href='photos/preview.png'>"+
            "<img src='stickers/actual.tgs'></a></div>");
        boolean seen=false;
        for(TelegramImporter.Media m:TelegramImporter.findMedia(msg))
            if(m.path.equals("stickers/actual.tgs")&&m.kind.equals("sticker"))seen=true;
        assertTrue(seen);
    }
    @Test public void originalPhotoNotDuplicatedByThumbnail() {
        assertEquals(1, TelegramImporter.findMedia(message("<div class='photo_wrap'><a href='photos/1.jpg'><img src='photos/1_thumb.jpg'></a></div>")).size());
    }
    @Test public void pngPreviewNotDoubleCounted() {
        assertEquals(1, TelegramImporter.findMedia(message("<div class='photo_wrap'><a href='photos/2.jpg'><img src='photos/2_preview.png'></a></div>")).size());
    }
    @Test public void multipleUnlinkedPhotosAllIndexed() {
        Element msg=message("<div class='photo_wrap'><img src='photos/1.jpg'></div>"+
            "<div class='photo_wrap'><img src='photos/2.jpg'></div>");
        assertEquals(2,TelegramImporter.findMedia(msg).size());
    }
    @Test public void unlinkedPhotoSurvivesUnrelatedFileAnchor() {
        Element msg=message("<a href='files/report.pdf'>Report</a>"+
            "<div class='photo_wrap'><img src='photos/3.jpg'></div>");
        assertEquals(2,TelegramImporter.findMedia(msg).size());
    }
    @Test public void linkedImagePreviewDoesNotCreateExtraAttachment() {
        Element msg=message("<div class='photo_wrap'><a href='photos/full.jpg'>"+
            "<img src='photos/small.jpg'></a></div>");
        assertEquals(1,TelegramImporter.findMedia(msg).size());
        assertEquals("photos/full.jpg",TelegramImporter.findMedia(msg).get(0).path);
    }
    @Test public void rootGifsFolderIsAnimatedNotOrdinaryVideo() {
        assertEquals("gif", TelegramImporter.classify("gifs/anim.mp4",message("<video src='gifs/anim.mp4'></video>")));
    }
    @Test public void rootAnimationsFolderIsAnimatedNotOrdinaryVideo() {
        assertEquals("gif", TelegramImporter.classify("animations/anim.mp4",message("<video src='animations/anim.mp4'></video>")));
    }
    @Test public void voiceOggNotMisclassifiedAsMusic() {
        assertEquals("voice", TelegramImporter.classify("voice_messages/a.ogg",message("<audio src='voice_messages/a.ogg'></audio>")));
    }
    @Test public void musicOggStillAudio() {
        assertEquals("audio", TelegramImporter.classify("music/a.ogg",message("<audio src='music/a.ogg'></audio>")));
    }
}
