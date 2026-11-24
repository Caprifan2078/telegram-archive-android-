package org.openarchive.telegram;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Instrumentation-only SAF surrogate. Serves small synthetic ZIP/7z fixtures
 * through a pipe, exactly like non-seekable third-party document providers.
 * This provider is NOT packaged in the application APK.
 */
public final class ArchivePipeTestProvider extends ContentProvider {
    static final String AUTHORITY="org.openarchive.telegram.qa.synthetic.pipe";
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return "application/octet-stream";}
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws IOException{
        if(!"r".equals(mode))throw new java.io.FileNotFoundException("Read only fixture");
        String name=uri.getLastPathSegment();
        if(name==null||!name.matches("[a-f0-9]{32}\\.(zip|7z)"))
            throw new java.io.FileNotFoundException("Unknown synthetic fixture");
        File source=new File(getContext().getCacheDir(),name);
        if(!source.isFile()||source.length()>2L*1024*1024)
            throw new java.io.FileNotFoundException("No bounded fixture");
        ParcelFileDescriptor[] pair=ParcelFileDescriptor.createPipe();
        Thread copy=new Thread(()->{
            try(FileInputStream in=new FileInputStream(source);
                FileOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pair[1])){
                byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))!=-1)out.write(buf,0,n);
            }catch(IOException ignored){
                try{pair[1].close();}catch(IOException ignoredClose){}
            }
        },"synthetic-archive-pipe");
        copy.setDaemon(true);
        copy.start();
        return pair[0];
    }
}
