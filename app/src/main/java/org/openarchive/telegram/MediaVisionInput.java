package org.openarchive.telegram;
import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Decode only the user-selected local attachment, in bounded RAM. */
final class MediaVisionInput {
    static final int MAX_EDGE=768, VIDEO_FRAME=256;
    static final class Sample {
        final byte[] jpeg; final int frames;
        Sample(byte[] bytes,int frames){jpeg=bytes;this.frames=frames;}
    }
    static Sample read(Context ctx,Uri uri,boolean video,AtomicBoolean cancel)throws Exception{
        if(uri==null||(!"file".equals(uri.getScheme())&&!"content".equals(uri.getScheme())))throw new IOException("Unsupported URI");
        check(cancel);
        if(video)return video(ctx,uri,cancel);
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream in=ctx.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Missing image");BitmapFactory.decodeStream(in,null,bounds);}
        if(bounds.outWidth<=0||bounds.outHeight<=0||(long)bounds.outWidth*bounds.outHeight>120000000L)throw new IOException("Image size");
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/opts.inSampleSize>MAX_EDGE)opts.inSampleSize*=2;
        Bitmap bitmap;
        try(InputStream in=ctx.getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Missing image");bitmap=BitmapFactory.decodeStream(in,null,opts);}
        if(bitmap==null)throw new IOException("Image decode");
        try{check(cancel);return new Sample(jpeg(bitmap),1);}finally{bitmap.recycle();}
    }
    private static Sample video(Context ctx,Uri uri,AtomicBoolean cancel)throws Exception{
        if(Build.VERSION.SDK_INT<27)throw new IOException("Video sampling requires Android 8.1");
        MediaMetadataRetriever reader=new MediaMetadataRetriever();Bitmap sheet=null;
        try{
            reader.setDataSource(ctx,uri);
            long duration=Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            int width=Integer.parseInt(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height=Integer.parseInt(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            if(duration<=0||duration>24L*60*60*1000||width<=0||height<=0||(long)width*height>35000000L)throw new IOException("Video size");
            sheet=Bitmap.createBitmap(VIDEO_FRAME*3,VIDEO_FRAME,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(sheet);canvas.drawColor(Color.BLACK);
            int count=0;
            for(int i=0;i<3;i++){
                check(cancel);
                long at=duration*(1+4*i)/10*1000L;
                Bitmap frame=reader.getScaledFrameAtTime(at,MediaMetadataRetriever.OPTION_CLOSEST,VIDEO_FRAME,VIDEO_FRAME);
                if(frame==null)continue;
                try{
                    float scale=Math.min((float)VIDEO_FRAME/frame.getWidth(),(float)VIDEO_FRAME/frame.getHeight());
                    float fw=frame.getWidth()*scale,fh=frame.getHeight()*scale;
                    float left=count*VIDEO_FRAME+(VIDEO_FRAME-fw)/2,top=(VIDEO_FRAME-fh)/2;
                    canvas.drawBitmap(frame,null,new RectF(left,top,left+fw,top+fh),new Paint(Paint.FILTER_BITMAP_FLAG));
                    count++;
                }finally{frame.recycle();}
            }
            if(count==0)throw new IOException("No video frames");
            if(count<3){
                Bitmap crop=Bitmap.createBitmap(sheet,0,0,count*VIDEO_FRAME,VIDEO_FRAME);
                try{return new Sample(jpeg(crop),count);}finally{if(crop!=sheet)crop.recycle();}
            }
            return new Sample(jpeg(sheet),count);
        }finally{if(sheet!=null)sheet.recycle();reader.release();}
    }
    private static void check(AtomicBoolean cancel)throws InterruptedException{if(cancel.get())throw new InterruptedException();}
    static byte[] jpeg(Bitmap bitmap)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        if(!bitmap.compress(Bitmap.CompressFormat.JPEG,88,out)||out.size()==0||out.size()>2*1024*1024)throw new IOException("Image encoding");
        return out.toByteArray();
    }
    private MediaVisionInput(){}
}
