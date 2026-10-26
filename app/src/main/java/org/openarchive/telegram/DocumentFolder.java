package org.openarchive.telegram;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only document tree helper: doesn't enumerate or copy large media folders during import. */
class DocumentFolder {
    static class Entry { String id,name,mime; Entry(String i,String n,String m){id=i;name=n;mime=m;} }
    private final ContentResolver resolver;
    private final Uri tree;
    private final java.io.File localRoot;
    private final String rootId;
    private final Map<String,String> folderIds = new HashMap<>();
    private final Map<String,Map<String,Entry>> lookupCache = new LinkedHashMap<String,Map<String,Entry>>(4,0.75f,true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,Map<String,DocumentFolder.Entry>> e) { return size()>3; }
    };
    DocumentFolder(ContentResolver r,Uri uri) {
        resolver=r; tree=uri; localRoot=null;rootId=DocumentsContract.getTreeDocumentId(uri);
        folderIds.put("",rootId);
    }
    DocumentFolder(ContentResolver r,java.io.File root) {
        resolver=r;tree=null;localRoot=root;rootId=root.getAbsolutePath();folderIds.put("",rootId);
    }
    List<Entry> children(String parentId) {
        if(localRoot!=null){ArrayList<Entry> out=new ArrayList<>();java.io.File[] files=new java.io.File(parentId).listFiles();if(files!=null)for(java.io.File f:files)out.add(new Entry(f.getAbsolutePath(),f.getName(),f.isDirectory()?DocumentsContract.Document.MIME_TYPE_DIR:"application/octet-stream"));return out;}
        ArrayList<Entry> result=new ArrayList<>();
        Uri uri=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId);
        String[] columns={DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE};
        try(Cursor c=resolver.query(uri,columns,null,null,null)) {
            if(c!=null)while(c.moveToNext())result.add(new Entry(c.getString(0),c.getString(1),c.getString(2)));
        }
        return result;
    }
    String rootId(){ return rootId; }
    Uri uriFor(String id) { return localRoot!=null?Uri.fromFile(new java.io.File(id)):DocumentsContract.buildDocumentUriUsingTree(tree,id); }
    List<Entry> htmlParts() {
        List<Entry> parts=new ArrayList<>();
        for(Entry e:children(rootId))if(e.name.matches("(?i)^messages\\d*\\.html$"))parts.add(e);
        Collections.sort(parts,(a,b)->Integer.compare(partNo(a.name),partNo(b.name)));
        return parts;
    }
    static int partNo(String s) {
        String n=s.replaceAll("(?i)^messages(\\d*)\\.html$","$1");
        try{return n.isEmpty()?1:Integer.parseInt(n);}catch(Exception e){return Integer.MAX_VALUE;}
    }
    /** Resolve relative export path on demand; reject directory traversal and external URLs. */
    Uri resolve(String relative) {
        if(relative==null||relative.isEmpty())return null;
        String s=Uri.decode(relative).replace('\\','/');
        while(s.startsWith("./"))s=s.substring(2);
        s=s.split("[?#]",2)[0];
        if(s.startsWith("/") || s.contains(":") || s.startsWith("//"))return null;
        if(localRoot!=null){try{java.io.File file=new java.io.File(localRoot,s);String base=localRoot.getCanonicalPath()+java.io.File.separator;if(file.isFile()&&file.getCanonicalPath().startsWith(base))return Uri.fromFile(file);}catch(Exception ignored){}return null;}
        String[] segments=s.split("/");
        String parent=rootId; String prefix="";
        for(int i=0;i<segments.length;i++) {
            String part=segments[i];
            if(part.isEmpty() || part.equals(".") || part.equals(".."))return null;
            boolean folder=i<segments.length-1;
            if(folder) {
                prefix+=(prefix.isEmpty()?"":"/")+part;
                String cached=folderIds.get(prefix);
                if(cached!=null){parent=cached;continue;}
            }
            Entry match;
            Map<String,Entry> matches=lookupCache.get(parent);
            if(matches==null) {
                matches=new HashMap<>();
                for(Entry e:children(parent))matches.put(e.name,e);
                lookupCache.put(parent,matches);
            }
            match=matches.get(part);
            if(match==null)return null;
            if(folder) {
                if(!DocumentsContract.Document.MIME_TYPE_DIR.equals(match.mime))return null;
                parent=match.id;folderIds.put(prefix,parent);
            } else return uriFor(match.id);
        }
        return null;
    }
}
