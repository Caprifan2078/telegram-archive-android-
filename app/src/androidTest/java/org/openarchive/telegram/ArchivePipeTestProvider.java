package org.openarchive.telegram;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayInputStream;
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

    /**
     * Android SAF providers can return a sliced AssetFileDescriptor, which can
     * be streamed but is not a whole-file descriptor for random-access APIs.
     */
    @Override public android.content.res.AssetFileDescriptor openAssetFile(Uri uri,String mode)
            throws java.io.FileNotFoundException{
        if(uri.getPathSegments().size()==2&&"stream".equals(uri.getPathSegments().get(0))){
            if(!"r".equals(mode))throw new java.io.FileNotFoundException("Read only");
            String payload=uri.getLastPathSegment();
            if(payload==null||payload.length()>8192||
                    !payload.matches("[A-Za-z0-9_-]+"))
                throw new java.io.FileNotFoundException("Invalid synthetic stream");
            byte[] bytes;
            try{bytes=android.util.Base64.decode(payload,
                android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP);}
            catch(IllegalArgumentException bad){
                throw new java.io.FileNotFoundException("Invalid synthetic stream payload");
            }
            if(bytes.length==0||bytes.length>6144)
                throw new java.io.FileNotFoundException("Oversized synthetic stream");
            ParcelFileDescriptor[] pair;
            try{pair=ParcelFileDescriptor.createPipe();}
            catch(IOException ex){java.io.FileNotFoundException failure=
                new java.io.FileNotFoundException("Pipe unavailable");
                failure.initCause(ex);throw failure;}
            Thread writer=new Thread(()->{
                try(ByteArrayInputStream in=new ByteArrayInputStream(bytes);
                    FileOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pair[1])){
                    byte[] block=new byte[2048];int n;
                    while((n=in.read(block))!=-1)out.write(block,0,n);
                }catch(IOException ignored){
                    try{pair[1].close();}catch(IOException ignoredClose){}
                }
            },"synthetic-asset-stream");
            writer.setDaemon(true);writer.start();
            // Declared length makes ContentResolver.openFileDescriptor reject
            // this as a whole file, but openInputStream still succeeds.
            return new android.content.res.AssetFileDescriptor(pair[0],0,bytes.length);
        }
        if(uri.getPathSegments().size()==2&&"layout".equals(uri.getPathSegments().get(0))){
            if(!"r".equals(mode)||!"solid-header".equals(uri.getLastPathSegment()))
                throw new java.io.FileNotFoundException("Unknown fixture");
            final java.io.InputStream asset;
            try{asset=getContext().getAssets().open("external7zip/layout-solid-header.7z");}
            catch(IOException failure){throw new java.io.FileNotFoundException("Missing layout asset");}
            ParcelFileDescriptor[] pair;
            try{pair=ParcelFileDescriptor.createPipe();}
            catch(IOException failure){try{asset.close();}catch(IOException ignored){}throw new java.io.FileNotFoundException("Pipe unavailable");}
            Thread writer=new Thread(()->{
                try(java.io.InputStream in=asset;FileOutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pair[1])){
                    byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
                }catch(IOException ignored){}
            },"synthetic-layout-stream");writer.setDaemon(true);writer.start();
            return new android.content.res.AssetFileDescriptor(pair[0],0,android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH);
        }
        return super.openAssetFile(uri,mode);
    }

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws java.io.FileNotFoundException{
        if(!"r".equals(mode))throw new java.io.FileNotFoundException("Read only fixture");
        // The instrumentation APK has a separate data directory from the
        // tested app. Never attempt to open the test APK cache from the target
        // process; the tiny synthetic fixture is encoded directly in the URI.
        java.util.List<String> parts=uri.getPathSegments();
        if(parts.size()!=2||!"fixture".equals(parts.get(0)))
            throw new java.io.FileNotFoundException("Unknown fixture path");
        String payload=parts.get(1);
        if(payload.length()>8192||!payload.matches("[A-Za-z0-9_-]+"))
            throw new java.io.FileNotFoundException("Oversize or invalid fixture");
        final byte[] bytes;
        try{bytes=android.util.Base64.decode(payload,
            android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP);}
        catch(IllegalArgumentException invalid){
            throw new java.io.FileNotFoundException("Invalid fixture bytes");
        }
        if(bytes.length==0||bytes.length>6144)
            throw new java.io.FileNotFoundException("Fixture exceeds test-only bound");
        ParcelFileDescriptor[] pair;
        try{pair=ParcelFileDescriptor.createPipe();}
        catch(IOException ex){java.io.FileNotFoundException failure=new java.io.FileNotFoundException("Pipe unavailable");failure.initCause(ex);throw failure;}
        Thread copy=new Thread(()->{
            try(ByteArrayInputStream in=new ByteArrayInputStream(bytes);
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
