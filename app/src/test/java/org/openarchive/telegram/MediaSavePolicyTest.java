package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaSavePolicyTest {
    @Test public void photoAndGifGoToGallery(){
        assertEquals(MediaSavePolicy.Destination.PICTURES,MediaSavePolicy.plan("photos/pic.JPG","image",false).destination);
        assertEquals("image/gif",MediaSavePolicy.plan("gifs/animated.gif","gif",false).mime);
    }
    @Test public void roundAndVideoGoToGallery(){
        assertEquals(MediaSavePolicy.Destination.MOVIES,MediaSavePolicy.plan("video_messages/clip.mp4","round",false).destination);
        assertEquals("video/webm",MediaSavePolicy.plan("video/a.webm","video",false).mime);
    }
    @Test public void audioRetainsOriginalFormat(){
        MediaSavePolicy.Spec voice=MediaSavePolicy.plan("voice_messages/voice.ogg","voice",false);
        assertEquals("audio/ogg",voice.mime);
        assertFalse(MediaSavePolicy.isActualMp3(voice));
        assertEquals(MediaSavePolicy.Destination.MUSIC,voice.destination);
        assertTrue(MediaSavePolicy.isActualMp3(MediaSavePolicy.plan("music/track.mp3","audio",false)));
        assertEquals("audio/opus",MediaSavePolicy.plan("sound.opus","voice",false).mime);
    }
    @Test public void noFakeTgsGalleryAndMissingExtension(){
        assertEquals(MediaSavePolicy.Destination.DOWNLOADS,MediaSavePolicy.plan("stickers/s.tgs","sticker",false).destination);
        assertEquals(MediaSavePolicy.Destination.DOWNLOADS,MediaSavePolicy.plan("nothing","image",false).destination);
    }
    @Test public void explicitDownloadWorksForVideos(){
        assertEquals(MediaSavePolicy.Destination.DOWNLOADS,MediaSavePolicy.plan("video.mp4","video",true).destination);
    }
    @Test public void filenamePathTraversalAndQueriesCannotBeExported(){
        assertEquals("a_b.mp4",MediaSavePolicy.plan("../../a:b.mp4?test=x","video",false).filename);
        assertEquals("file",MediaSavePolicy.plan("..","file",false).filename);
        assertEquals("image/png",MediaSavePolicy.plan("images/foo.png#fragment","image",false).mime);
    }
    @Test public void extensionDoesNotInventFormat(){
        assertEquals("application/octet-stream",MediaSavePolicy.plan("audio.unknown","voice",false).mime);
        assertEquals(MediaSavePolicy.Destination.DOWNLOADS,MediaSavePolicy.plan("audio.unknown","voice",false).destination);
    }
}
