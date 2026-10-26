package org.openarchive.telegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small, deterministic window of locally indexed music. No scanning media bytes or network. */
final class MusicQueue {
    static final int BEFORE=120, AFTER=121;
    static final class Track {
        final long attachmentId;
        final String path, author, date;
        Track(long id,String path,String author,String date){
            this.attachmentId=id;
            this.path=path==null?"":path;
            this.author=author==null?"":author;
            this.date=date==null?"":date;
        }
    }
    final List<Track> tracks;
    final int selected;
    MusicQueue(List<Track> olderDescending,List<Track> selectedAndNewer,long selectedId){
        ArrayList<Track> combined=new ArrayList<>();
        if(olderDescending!=null){
            ArrayList<Track> chronological=new ArrayList<>(olderDescending);
            Collections.reverse(chronological);combined.addAll(chronological);
        }
        if(selectedAndNewer!=null)combined.addAll(selectedAndNewer);
        int anchor=-1;
        for(int i=0;i<combined.size();i++){
            if(combined.get(i).attachmentId==selectedId){anchor=i;break;}
        }
        tracks=Collections.unmodifiableList(combined);
        selected=anchor;
    }
    int adjacent(int index,boolean next){
        int target=index+(next?1:-1);
        return target<0||target>=tracks.size()?-1:target;
    }
    static String cleanTitle(String path){
        if(path==null||path.trim().isEmpty())return "Без названия";
        String name=path.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);
        name=name.replaceFirst("(?i)\\.(mp3|ogg|opus|m4a|wav|flac|aac|wma)$", "");
        return name.trim().isEmpty()?"Без названия":name;
    }
    static boolean validChatKey(String id){
        return id!=null&&id.matches("[a-zA-Z0-9_-]{1,80}");
    }
}
