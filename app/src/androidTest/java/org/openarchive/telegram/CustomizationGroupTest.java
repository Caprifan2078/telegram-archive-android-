package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

/** Three different senders represent a Telegram group HTML export. One locally
 * stored avatar may intentionally be assigned to multiple participants. */
@RunWith(AndroidJUnit4.class)
public class CustomizationGroupTest {
    @Test public void groupImportAndSharedAvatarAndWallpaper()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=UUID.randomUUID().toString().replace("-","");
        File folder=new File(ctx.getCacheDir(),"group-fixture-"+id);assertTrue(folder.mkdirs());
        File html=new File(folder,"messages.html");
        try(Writer out=new OutputStreamWriter(new FileOutputStream(html),"UTF-8")){
            out.write("<div class='history'>");
            for(String name:new String[]{"Аня","Борис","Вика"}){
                out.write("<div class='message default'><div class='body'><div class='from_name'>"+name+
                    "</div><div class='text'>Групповое сообщение</div></div></div>");
            }
            out.write("</div>");
        }
        Bitmap bmp=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.BLUE);
        File source=new File(folder,"portrait.png");
        try(OutputStream out=new FileOutputStream(source)){assertTrue(bmp.compress(Bitmap.CompressFormat.PNG,100,out));}
        bmp.recycle();
        MessageDb db=new MessageDb(ctx,id);
        SharedPreferences prefs=ctx.getSharedPreferences("group-test-"+id,Context.MODE_PRIVATE);
        String file=null;
        try{
            assertEquals(3,TelegramImporter.importFiles(ctx.getContentResolver(),new DocumentFolder(ctx.getContentResolver(),folder),db,msg->{}));
            List<String> people=db.participants();assertEquals(3,people.size());
            file=PersonalImages.save(ctx,Uri.fromFile(source),id);
            SharedPreferences.Editor edit=prefs.edit();
            edit.putString(PersonalImages.avatarKey(id,"Аня"),file);
            edit.putString(PersonalImages.avatarKey(id,"Борис"),file);
            edit.putString(PersonalImages.chatAvatarKey(id),file);
            edit.putString(PersonalImages.wallpaperKey(id),file);
            assertTrue(edit.commit());
            assertEquals(prefs.getString(PersonalImages.avatarKey(id,"Аня"),""),prefs.getString(PersonalImages.avatarKey(id,"Борис"),""));
            assertNotNull(PersonalImages.load(ctx,file));
            assertNotNull(PersonalImages.wallpaper(ctx,prefs,id,0xff111111));
            assertEquals(file,prefs.getString(PersonalImages.chatAvatarKey(id),""));
        }finally{
            db.close();ctx.deleteDatabase("telegram_"+id+".db");
            prefs.edit().clear().commit();ArchiveFileImport.deleteTree(folder);
            if(file!=null)new File(new File(ctx.getFilesDir(),"personal_images"),file).delete();
        }
    }
}
