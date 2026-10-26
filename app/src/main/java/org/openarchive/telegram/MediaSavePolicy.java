package org.openarchive.telegram;

import java.util.Locale;

/** Pure filename, MIME and destination selection. Never relabels audio as MP3. */
final class MediaSavePolicy {
    enum Destination { PICTURES, MOVIES, MUSIC, DOWNLOADS }
    static final class Spec {
        final String filename, mime;
        final Destination destination;
        Spec(String filename, String mime, Destination destination) {
            this.filename=filename; this.mime=mime; this.destination=destination;
        }
    }
    private MediaSavePolicy() { }
    static Spec plan(String path, String kind, boolean downloads) {
        String raw=path==null?"":path.replace('\\','/');
        int query=raw.indexOf('?'); if(query>=0)raw=raw.substring(0,query);
        int fragment=raw.indexOf('#'); if(fragment>=0)raw=raw.substring(0,fragment);
        String name=raw.substring(raw.lastIndexOf('/')+1).replaceAll("[^\\p{L}\\p{N}._() -]","_").trim();
        if(name.equals(".")||name.equals("..")||name.isEmpty())name="file";
        if(name.length()>90)name=name.substring(name.length()-90);
        String ext="";
        int dot=name.lastIndexOf('.'); if(dot>0&&dot<name.length()-1)ext=name.substring(dot+1).toLowerCase(Locale.ROOT);
        String mime;
        switch(ext) {
            case "jpg":case "jpeg": mime="image/jpeg";break;
            case "png": mime="image/png";break;
            case "webp": mime="image/webp";break;
            case "gif": mime="image/gif";break;
            case "heic":case "heif": mime="image/heif";break;
            case "bmp": mime="image/bmp";break;
            case "mp4":case "m4v": mime="video/mp4";break;
            case "mov": mime="video/quicktime";break;
            case "webm": mime="video/webm";break;
            case "mkv": mime="video/x-matroska";break;
            case "3gp": mime="video/3gpp";break;
            case "mp3": mime="audio/mpeg";break;
            case "ogg":case "oga": mime="audio/ogg";break;
            case "opus": mime="audio/opus";break;
            case "m4a": mime="audio/mp4";break;
            case "aac": mime="audio/aac";break;
            case "flac": mime="audio/flac";break;
            case "wav": mime="audio/wav";break;
            case "tgs": mime="application/x-tgsticker";break;
            case "txt": mime="text/plain";break;
            default: mime="application/octet-stream";
        }
        String type=kind==null?"":kind.toLowerCase(Locale.ROOT);
        Destination dest=Destination.DOWNLOADS;
        if(!downloads) {
            if(mime.startsWith("image/"))dest=Destination.PICTURES;
            else if(mime.startsWith("video/"))dest=Destination.MOVIES;
            else if(mime.startsWith("audio/"))dest=Destination.MUSIC;
            else if(type.equals("round")||type.equals("video"))dest=Destination.MOVIES;
            else if(type.equals("voice")||type.equals("audio"))dest=Destination.MUSIC;
        }
        // Missing original extension must not cause a fake format or an invalid Gallery entry.
        if(mime.equals("application/octet-stream")||mime.equals("application/x-tgsticker"))dest=Destination.DOWNLOADS;
        return new Spec(name,mime,dest);
    }
    static boolean isActualMp3(Spec spec){return "audio/mpeg".equals(spec.mime);}
}
