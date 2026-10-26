package org.openarchive.telegram;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Synthetic Telegram HTML only; never reads a private chat or user media. */
public class TelegramMediaClassificationRegressionTest {
    private Element element(String klass) {
        return Jsoup.parse("<div class='message'><div class='"+klass+"'><a href='media/test.mp4'>open</a></div></div>").selectFirst("a");
    }
    @Test public void recognizesAnimatedAndStaticStickersBeforePhoto(){
        assertEquals("sticker",TelegramImporter.classify("stickers/sticker_1.webp",element("sticker_wrap")));
        assertEquals("sticker",TelegramImporter.classify("stickers/animated_1.tgs",element("sticker_wrap")));
    }
    @Test public void vp9VideoStickerMustRemainStickerNotOrdinaryVideo(){
        assertEquals("sticker",TelegramImporter.classify("stickers/loop.webm",element("sticker_wrap")));
        assertEquals("video",TelegramImporter.classify("videos/loop.webm",element("video_wrap")));
    }
    @Test public void gifAndVideoHaveDifferentTypes(){
        assertEquals("gif",TelegramImporter.classify("animations/animation_001.mp4",element("animated")));
        assertEquals("gif",TelegramImporter.classify("photos/smile.gif",element("photo_wrap")));
        assertEquals("video",TelegramImporter.classify("video_files/video_001.mp4",element("video_wrap")));
    }
    @Test public void voiceAndMusicHaveDifferentTypes(){
        assertEquals("voice",TelegramImporter.classify("voice_messages/audio_01.ogg",element("voice_message")));
        assertEquals("audio",TelegramImporter.classify("audio/music_01.mp3",element("audio")));
    }
    @Test public void roundAndRegularVideoSeparated(){
        assertEquals("round",TelegramImporter.classify("video_messages/round_video_01.mp4",element("round_video")));
        assertEquals("video",TelegramImporter.classify("video_files/movie_01.mp4",element("video")));
    }
    @Test public void gifWithStickerInFilenameIsStillGif(){
        assertEquals("gif",TelegramImporter.classify("gifs/sticker_081.gif",element("animated")));
        assertEquals("gif",TelegramImporter.classify("animations/sticker_123.mp4",element("animated")));
        assertEquals("image",TelegramImporter.classify("photos/sticker.jpg",element("photo_wrap")));
        assertEquals("sticker",TelegramImporter.classify("stickers/anything.gif",element("sticker_wrap")));
    }
}
