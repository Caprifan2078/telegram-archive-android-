package org.openarchive.telegram;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Streaming SHA-256 of each media entry's full path, contents and length.
 * Ordered ZIP-entry transcript: no media file buffering and no growing in-memory index.
 * Full backup v2 may omit mediaSha256 for backward compatibility with older backups. */
final class MediaIntegrity {
    private final MessageDigest sha;
    private boolean active;
    private long bytes;
    MediaIntegrity() {
        try { sha=MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private void length(long value) {
        for(int i=7;i>=0;i--)sha.update((byte)(value >>> (i*8)));
    }
    void begin(String path) {
        if(active)throw new IllegalStateException("Unfinished media entry");
        if(path==null||!(path.startsWith("media/")||path.startsWith("custom/")||path.startsWith("note_files/")))throw new IllegalArgumentException("Invalid media path");
        byte[] name=path.getBytes(StandardCharsets.UTF_8);
        sha.update((byte)0x46); // file marker
        length(name.length);sha.update(name);
        bytes=0;active=true;
    }
    void update(byte[] buf,int offset,int count){
        if(!active)throw new IllegalStateException("No active media entry");
        if(count<0||offset<0||offset>buf.length-count)throw new IndexOutOfBoundsException();
        sha.update(buf,offset,count);bytes+=count;
    }
    void end() {
        if(!active)throw new IllegalStateException("No active media entry");
        sha.update((byte)0x45); // end marker
        length(bytes);active=false;
    }
    String hex() {
        if(active)throw new IllegalStateException("Unfinished media entry");
        StringBuilder output=new StringBuilder(64);
        for(byte x:sha.digest())output.append(String.format(Locale.ROOT,"%02x",x&255));
        return output.toString();
    }
}
